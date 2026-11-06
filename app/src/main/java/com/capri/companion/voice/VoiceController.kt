package com.capri.companion.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.capri.companion.brain.BrainClient
import com.capri.companion.data.CapriSettings
import com.capri.companion.data.VoiceMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale

class VoiceController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onSpeaking: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val qwen: QwenVoice,
    private val onAmplitude: (Float) -> Unit,
    private val makeQwenSpeech: suspend (String) ->
        com.qwen.tts.studio.engine.QwenEngine.NativeResult = { qwen.synthesize(it) }
) {
    private var ready = false
    private var initializationFinished = false
    private var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null
    private var audioFile: File? = null
    private var job: Job? = null

    @Volatile private var generation = 0L
    private var pending: (() -> Unit)? = null
    @Volatile private var track: AudioTrack? = null
    private val audioGuard = Any()

    val preparing = kotlinx.coroutines.flow.MutableStateFlow(false)

    private data class AudioChunk(
        val samples: FloatArray,
        val sampleRate: Int
    )

    init {
        tts = TextToSpeech(context) { status ->
            scope.launch(Dispatchers.Main) {
                ready = status == TextToSpeech.SUCCESS
                initializationFinished = true

                if (ready) pending?.invoke()
                else if (pending != null) {
                    preparing.value = false
                    onError("No se pudo iniciar la voz del sistema.")
                }

                pending = null
            }
        }
    }

    fun speak(text: String, settings: CapriSettings) {
        stop()

        if (settings.voiceMode == VoiceMode.OFF || settings.volume == 0f) return

        val clean = text.replace(Regex("\\[[^]]*]"), " ")
            .replace("*", "").trim()

        if (clean.isBlank()) return

        if (settings.voiceMode == VoiceMode.QWEN) {
            preparing.value = true
            val token = generation

            job = scope.launch {
    coroutineScope {
                val channel = Channel<AudioChunk>(capacity = 4)
                var writer: Job? = null
                var audio: AudioTrack? = null
                var played = false
                var totalWritten = 0L

                try {
                    
                    writer = launch(Dispatchers.IO) {
                        try {
                            for (chunk in channel) {
                                currentCoroutineContext().ensureActive()
                                if (token != generation) break

                                if (audio == null) {
                                    val minBuffer = AudioTrack.getMinBufferSize(
                                        chunk.sampleRate,
                                        AudioFormat.CHANNEL_OUT_MONO,
                                        AudioFormat.ENCODING_PCM_FLOAT
                                    )

                                    check(minBuffer > 0) {
                                        "El teléfono no admite este formato de audio."
                                    }

                                    val bufferBytes = maxOf(
                                        minBuffer * 2,
                                        chunk.sampleRate * Float.SIZE_BYTES
                                    )

                                    audio = AudioTrack.Builder()
                                        .setAudioAttributes(
                                            AudioAttributes.Builder()
                                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                .build()
                                        )
                                        .setAudioFormat(
                                            AudioFormat.Builder()
                                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                                .setSampleRate(chunk.sampleRate)
                                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                                .build()
                                        )
                                        .setBufferSizeInBytes(bufferBytes)
                                        .setTransferMode(AudioTrack.MODE_STREAM)
                                        .build()
                                        .also {
                                            synchronized(audioGuard) {
                                                if (token != generation) {
                                                    it.release()
                                                    error("Reproducción cancelada.")
                                                }
                                                track = it
                                            }
                                            it.setVolume(settings.volume)
                                        }
                                }

                                check(chunk.sampleRate == audio!!.sampleRate) {
                                    "Qwen cambió la frecuencia del audio."
                                }

                                var offset = 0

                                while (offset < chunk.samples.size) {
                                    currentCoroutineContext().ensureActive()
                                    if (token != generation) break

                                    val written = audio!!.write(
                                        chunk.samples,
                                        offset,
                                        chunk.samples.size - offset,
                                        AudioTrack.WRITE_BLOCKING
                                    )

                                    check(written > 0) {
                                        "No se pudo transmitir el audio de Qwen."
                                    }

                                    offset += written
                                    totalWritten += written

                                    if (!played) {
                                        synchronized(audioGuard) {
                                            if (token == generation) {
                                                audio!!.play()
                                                played = true
                                                preparing.value = false
                                                onSpeaking(true)
                                            }
                                        }
                                    }
                                }

                                if (played && token == generation) {
                                    val recent = chunk.samples
                                    var squares = 0.0
                                    for (sample in recent) {
                                        squares += sample.toDouble() * sample
                                    }

                                    val level = (
                                        kotlin.math.sqrt(
                                            squares / maxOf(1, recent.size)
                                        ) * 5
                                    ).toFloat().coerceIn(0f, 1f)

                                    scope.launch(Dispatchers.Main) {
                                        if (token == generation) onAmplitude(level)
                                    }
                                }
                            }

                            val currentAudio = audio
                            if (currentAudio != null && played && token == generation) {
                                while (token == generation) {
                                    currentCoroutineContext().ensureActive()

                                    val position =
                                        currentAudio.playbackHeadPosition.toLong() and 0xffffffffL

                                    if (position >= totalWritten) break
                                    delay(20)
                                }
                            }
                        } finally {
                            synchronized(audioGuard) {
                                audio?.let {
                                    runCatching {
                                        if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                            it.stop()
                                        }
                                        it.release()
                                    }
                                }
                                if (track === audio) track = null
                            }
                        }
                    }

                    val processorRef = arrayOfNulls<QwenVoiceEffects.StreamProcessor>(1)
                    var streamSampleRate = 24000

                    qwen.stream(clean, onAudio = { samples, sampleRate ->
                        if (token != generation) {
                            false
                        } else {
                            streamSampleRate = sampleRate
                            try {
                                var processor = processorRef[0]

                                if (processor == null) {
                                    processor = QwenVoiceEffects.StreamProcessor(
                                        sampleRate,
                                        settings.qwenSpeed,
                                        settings.qwenRobotic
                                    )
                                    processorRef[0] = processor
                                }

                                val processed = processor.process(samples)

                                if (processed.isNotEmpty()) {
                                    runBlocking {
                                        channel.send(AudioChunk(processed, sampleRate))
                                    }
                                }

                                token == generation
                            } catch (_: Exception) {
                                false
                            }
                        }
                    })

                    val processor = processorRef[0]
                    if (processor != null && token == generation) {
                        val finalSamples = processor.finish()
                        if (finalSamples.isNotEmpty()) {
                            channel.send(
                                AudioChunk(finalSamples, streamSampleRate)
                            )
                        }
                    }

                    channel.close()
                    writer.join()

                    if (token == generation && !played) {
                        error("Qwen no generó audio reproducible.")
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token == generation) {
                        onError(e.message ?: "Qwen no pudo hablar.")
                    }
                } finally {
                    channel.cancel()
                    writer?.cancel()
                    writer?.join()

                    synchronized(audioGuard) {
                        if (token == generation) {
                            preparing.value = false
                            onSpeaking(false)
                            onAmplitude(-1f)
                        }
                    }
                }
            }
            return
        }

        if (settings.voiceMode == VoiceMode.SYSTEM) {
            preparing.value = true

            if (ready) system(clean, settings)
            else if (initializationFinished) {
                preparing.value = false
                onError("No hay un motor de texto a voz disponible en este teléfono.")
            } else {
                pending = { system(clean, settings) }
            }
            return
        }

        if (settings.elevenKey.isBlank() || settings.elevenVoice.isBlank()) {
            onError("Guardá la clave y el Voice ID de ElevenLabs en Mainframe.")
            return
        }

        val token = generation
        preparing.value = true

        job = scope.launch {
            try {
                val json = JSONObject()
                    .put("text", clean.take(4000))
                    .put("model_id", settings.elevenModel)
                    .put("language_code", "es")

                val id = java.net.URLEncoder.encode(
                    settings.elevenVoice.trim(), "UTF-8"
                )

                val request = Request.Builder()
                    .url("https://api.elevenlabs.io/v1/text-to-speech/$id?output_format=mp3_44100_128")
                    .header("xi-api-key", settings.elevenKey.trim())
                    .post(
                        json.toString().toRequestBody(
                            "application/json".toMediaType()
                        )
                    )
                    .build()

                val audioBytes = BrainClient().execute(request)

                val file = withContext(Dispatchers.IO) {
                    File.createTempFile("capri-", ".mp3", context.cacheDir)
                        .apply { writeBytes(audioBytes) }
                }

                if (token != generation) {
                    file.delete()
                    return@launch
                }

                audioFile = file
                val mp = MediaPlayer()
                player = mp

                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )

                mp.setDataSource(file.absolutePath)
                mp.setVolume(settings.volume, settings.volume)

                mp.setOnPreparedListener {
                    if (token == generation) {
                        preparing.value = false
                        onSpeaking(true)
                        it.start()
                    }
                }

                mp.setOnCompletionListener {
                    if (token == generation) stop()
                }

                mp.setOnErrorListener { _, _, _ ->
                    if (token == generation) {
                        stop()
                        onError("No se pudo reproducir la voz. La respuesta sigue en el chat.")
                    }
                    true
                }

                mp.prepareAsync()

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == generation) {
                    preparing.value = false
                    onError(e.message ?: "La voz no respondió.")
                }
            }
        }
    }

    private fun system(text: String, settings: CapriSettings) {
        val engine = tts ?: run {
            preparing.value = false
            return
        }

        val locale = Locale.forLanguageTag("es-MX")
        val available = engine.setLanguage(locale)

        if (available < TextToSpeech.LANG_AVAILABLE) {
            preparing.value = false
            onError("Instalá una voz en español en los ajustes de texto a voz de Android.")
            return
        }

        engine.setSpeechRate(1.12f)
        engine.setPitch(1.10f)

        val token = generation

        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    scope.launch {
                        if (token == generation) {
                            preparing.value = false
                            onSpeaking(true)
                        }
                    }
                }

                override fun onDone(id: String?) {
                    scope.launch {
                        if (token == generation) {
                            preparing.value = false
                            onSpeaking(false)
                        }
                    }
                }

                @Deprecated("Android callback")
                override fun onError(id: String?) {
                    scope.launch {
                        if (token == generation) {
                            preparing.value = false
                            onSpeaking(false)
                            onError("La voz del sistema no pudo hablar.")
                        }
                    }
                }
            }
        )

        val code = engine.speak(
            text.take(4000),
            TextToSpeech.QUEUE_FLUSH,
            Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume)
            },
            "capri-$token"
        )

        if (code == TextToSpeech.ERROR) {
            preparing.value = false
            onError("La voz del sistema no está disponible.")
        }
    }

    fun stop() {
        synchronized(audioGuard) {
            generation++
            track?.let {
                runCatching {
                    it.pause()
                    it.flush()
                    it.release()
                }
            }
            track = null
        }

        preparing.value = false
        qwen.cancel()
        pending = null
        job?.cancel()
        job = null
        tts?.stop()
        player?.release()
        player = null
        audioFile?.delete()
        audioFile = null
        onSpeaking(false)
        onAmplitude(-1f)
    }
}
