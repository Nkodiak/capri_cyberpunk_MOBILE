package com.capri.companion

import android.app.Application
import com.capri.companion.avatar.SpriteAtlas
import com.capri.companion.brain.BrainClient
import com.capri.companion.data.CapriSettings
import com.capri.companion.data.CapriStore
import com.capri.companion.data.Message
import com.capri.companion.voice.VoiceController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.capri.companion.context.ContextController
import com.capri.companion.context.ContextSnapshot
import com.capri.companion.voice.QwenVoice

class CapriApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var store: CapriStore; private set
    lateinit var atlas: SpriteAtlas; private set
    lateinit var voice: VoiceController; private set
    lateinit var qwen: QwenVoice; private set
    lateinit var contextual: ContextController; private set
    lateinit var personalMemory: com.capri.companion.data.PersonalMemoryController
    private set
    lateinit var chatgpt: com.capri.companion.auth.ChatGptConnection; private set
    lateinit var brain: BrainClient; private set
    val settings = MutableStateFlow(CapriSettings())
    val messages = MutableStateFlow<List<Message>>(emptyList())
    val busy = MutableStateFlow(false)
    val speaking = MutableStateFlow(false)
    val listening = MutableStateFlow(false)
    val voiceChat = MutableStateFlow(com.capri.companion.voice.OverlayVoiceState())
    val composing = MutableStateFlow(false)
    val appVisible = MutableStateFlow(false)
    val hideOverlayForCapture = MutableStateFlow(false)
    val amplitude = MutableStateFlow(-1f)
    val initiativeSpeaking = MutableStateFlow(false)
    val avatarCue = kotlinx.coroutines.flow.MutableSharedFlow<com.capri.companion.avatar.Pose>(extraBufferCapacity = 2)
    val overlayRunning = MutableStateFlow(false)
    val notice = MutableStateFlow<String?>(null)
    private var request: Job? = null
    private var generation = 0L
    private val persistence = Mutex()

    override fun onCreate() {
        super.onCreate()
        store = CapriStore(this)
        atlas = SpriteAtlas(this)
        settings.value = store.loadSettings()
        messages.value = store.messages()
        chatgpt = com.capri.companion.auth.ChatGptConnection(store, scope)
        brain = BrainClient(chatgpt = chatgpt)
        qwen = QwenVoice(this, scope)
        voice = VoiceController(this, scope, { speaking.value = it; if (!it) initiativeSpeaking.value = false }, { notice.value = it }, qwen, { amplitude.value = it })
        contextual = ContextController(this)
        personalMemory = com.capri.companion.data.PersonalMemoryController(this)

scope.launch {
    try {
        personalMemory.refresh()
    } catch (e: Exception) {
        notice.value = "No se pudo cargar la memoria personal: ${e.message}"
    }
}
        qwen.installBundled()
        cacheDir.listFiles()?.filter { it.name.startsWith("capri-") && it.extension == "mp3" }?.forEach { it.delete() }
    }

    fun send(text: String) {
        val words = text.trim().take(4000)
        if (words.isEmpty() || busy.value) return
        contextual.userInteracted()
        val config = settings.value
        if (config.model.isBlank()) { notice.value = "Primero conectá un modelo en Mainframe → Cerebro."; return }
        voice.stop()
        notice.value = null
        messages.value = (messages.value + Message("user", words)).takeLast(80)
        busy.value = true
        val token = ++generation
        request = scope.launch {
            try {
                persist()
                val shared = contextual.snapshot.value?.takeIf { contextual.contextForChat() != null }
                val personal = personalMemory.memories.value
val personalJson = org.json.JSONArray().apply {
    personal.sortedByDescending { it.updatedAt }
        .take(20)
        .forEach { memory ->
            put(
                org.json.JSONObject()
                    .put("category", memory.category)
                    .put("content", memory.content)
                    .put("updatedAt", memory.updatedAt)
            )
        }
}

val activityJson = contextual.memoryForChat()
val memoryPayload = org.json.JSONObject()
    .put("personal", personalJson)
    .put("activities", activityJson?.let {
        org.json.JSONArray(it)
    } ?: org.json.JSONArray())
    .toString()

val response = brain.reply(
    config,
    messages.value,
    shared?.json(),
    memoryPayload
)
                if (shared != null && (contextual.snapshot.value?.session != shared.session || !contextual.state.value.active)) {
                    notice.value = "La sesión de pantalla terminó antes de recibir la respuesta. Volvé a preguntar para conversar sin ese contexto."
                    return@launch
                }
                messages.value = (messages.value + Message("assistant", response)).takeLast(80)
                persist()
                voice.speak(response, config)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { notice.value = e.message ?: "No se pudo recibir una respuesta." }
            finally { if (token == generation) busy.value = false }
        }
    }

    fun cancel() { generation++; request?.cancel(); request = null; busy.value = false; voice.stop() }
    fun stopForAccountChange() {
        com.capri.companion.voice.OverlayVoiceService.stop(this)
        cancel()
        stopScreenContext()
    }
    fun stopScreenContext() {
        contextual.end()
        if (BuildConfig.MANUAL_CAPTURE) com.capri.companion.context.ManualScreenshotService.stop(this)
        else com.capri.companion.context.ScreenshotService.stop()
    }
    fun testChatGpt(model: String) {
        if (busy.value) return
        busy.value = true
        val token = ++generation
        request = scope.launch {
            try {
                val answer = brain.complete(com.capri.companion.data.AiProvider.CHATGPT,
                    com.capri.companion.auth.ChatGptProtocol.API, model, "", "Respondé en español con una sola frase breve.",
                    listOf(Message("user", "Saludame como Capri y confirmá que recibiste este mensaje.")))
                notice.value = "Conexión comprobada: $answer"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { notice.value = e.message ?: "No se pudo comprobar la conexión con ChatGPT." }
            finally { if (token == generation) busy.value = false }
        }
    }
    fun replay() { messages.value.lastOrNull { it.role == "assistant" }?.let { voice.speak(it.text, settings.value) } }
    fun cancelInitiative() { if (initiativeSpeaking.value) { voice.stop(); initiativeSpeaking.value = false } }
    fun sayContext(snapshot: ContextSnapshot): Boolean {
        if (!snapshot.valid(System.currentTimeMillis()) || !contextual.state.value.active || !contextual.state.value.visible ||
            busy.value || speaking.value || voice.preparing.value || listening.value || composing.value) return false
        messages.value = (messages.value + Message("assistant", snapshot.result.reaction)).takeLast(80)
        avatarCue.tryEmit(com.capri.companion.avatar.Pose.GRIN)
        voice.speak(snapshot.result.reaction, settings.value)
        initiativeSpeaking.value = true
        scope.launch { runCatching { persist() } }
        return true
    }
    fun save(config: CapriSettings) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(config) }
                if (!config.autoComments && settings.value.autoComments) cancelInitiative()
                if (config.provider != settings.value.provider || config.model != settings.value.model ||
                    config.apiKey != settings.value.apiKey || config.endpoint != settings.value.endpoint) cancel()
                val warmVoice = config.voiceMode == com.capri.companion.data.VoiceMode.QWEN && settings.value.voiceMode != config.voiceMode
                if (config.voiceMode != settings.value.voiceMode) voice.stop()
                settings.value = config
                if (warmVoice) qwen.warmUp()
                notice.value = "Ajustes guardados."
            } catch (_: Exception) { notice.value = "No se pudieron guardar los ajustes. Probá otra vez." }
        }
    }
    fun clearConversation() {
        cancel()
        scope.launch {
            messages.value = emptyList()
            try { persist(); notice.value = "Conversación borrada de este teléfono." }
            catch (_: Exception) { notice.value = "No se pudo borrar la conversación guardada." }
        }
    }
    private suspend fun persist() {
        val snapshot = messages.value
        withContext(Dispatchers.IO) { persistence.withLock { store.saveMessages(snapshot) } }
    }
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); if (level >= TRIM_MEMORY_RUNNING_LOW) atlas.trim() }
}
