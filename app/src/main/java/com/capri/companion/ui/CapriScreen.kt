@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.capri.companion.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capri.companion.BuildConfig
import com.capri.companion.CapriApplication
import com.capri.companion.R
import com.capri.companion.avatar.CapriSpriteView
import com.capri.companion.avatar.Pose
import com.capri.companion.data.CapriSettings
import com.capri.companion.data.VoiceMode
import com.capri.companion.data.AiProvider
import com.capri.companion.data.Initiative

private val Night = Color(0xFF080B10)
private val Panel = Color(0xFF10161F)
private val Cyan = Color(0xFF4EDFF2)
private val Red = Color(0xFFFF315A)
private val Yellow = Color(0xFFF2D66D)
private val Ink = Color(0xFFE5EAF0)
private val Muted = Color(0xFF8D9BAE)
private val Edge = Color(0xFF2D3D4C)
private val Cut = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
private val Mono = FontFamily(Font(R.font.share_tech_mono))

@Composable
fun CapriScreen(app: CapriApplication, tab: Int, onTab: (Int) -> Unit,
    overlayAllowed: Boolean, partialSpeech: String, onShow: () -> Unit, onHide: () -> Unit, onMicrophone: () -> Unit,
    onShareScreen: () -> Unit, onStopObserving: () -> Unit, onChatGptSignIn: (String?) -> Unit, onImportVoice: () -> Unit) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val running by app.overlayRunning.collectAsStateWithLifecycle()
    val notice by app.notice.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Red, background = Night,
        surface = Panel, onSurface = Ink, onBackground = Ink, error = Red),
        typography = Typography(bodyLarge = TextStyle(fontFamily = Mono, fontSize = 16.sp),
            bodyMedium = TextStyle(fontFamily = Mono, fontSize = 14.sp), labelLarge = TextStyle(fontFamily = Mono, fontSize = 14.sp))) {
        Box(Modifier.fillMaxSize().background(Night)) {
            Scanlines()
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Header()
                Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().border(1.dp, Edge, Cut).clip(Cut)) {
                    listOf("MAINFRAME", "CONVERSAR", "ARCHIVO", "ENTORNO").forEachIndexed { index, text ->
                        Box(Modifier.weight(1f).height(48.dp).testTag("tab-$index").background(if (tab == index) Cyan.copy(alpha = .11f) else Color.Transparent)
                            .clickable { onTab(index) }, contentAlignment = Alignment.Center) {
                            Text(text, color = if (tab == index) Cyan else Muted, fontFamily = Mono, fontSize = 10.sp)
                        }
                    }
                }
                notice?.let {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().background(Panel, Cut).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(it, color = Yellow, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { app.notice.value = null }, contentPadding = PaddingValues(4.dp)) { Text("×", color = Muted) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> Mainframe(app, settings, running, overlayAllowed, onShow, onHide, { onTab(1) }, onChatGptSignIn, onImportVoice)
                        1 -> Conversation(app, partialSpeech, onMicrophone)
                        2 -> Archive(app)
                        else -> Environment(app, onShareScreen, onStopObserving, onShow)
                    }
                }
            }
        }
    }
}

@Composable private fun Header() {
    Row(Modifier.fillMaxWidth().padding(20.dp, 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.chip), contentDescription = null, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("CAPRI", fontFamily = Mono, fontSize = 27.sp, color = Ink, letterSpacing = 5.sp)
            Text("PROJECT CAPRICORN / ANDROID", color = Muted, fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.sp)
        }
        Text("v${BuildConfig.VERSION_NAME}\nES / MOBILE", color = Cyan, fontFamily = Mono, fontSize = 9.sp, lineHeight = 15.sp)
    }
}

@Composable private fun Scanlines() {
    Canvas(Modifier.fillMaxSize()) {
        var y = 0f
        while (y < size.height) { drawLine(Color.White.copy(alpha = .018f), Offset(0f, y), Offset(size.width, y)); y += 4.dp.toPx() }
    }
}

@Composable private fun Sprite(app: CapriApplication, modifier: Modifier = Modifier, preview: Pose? = null) {
    val speaking by app.speaking.collectAsStateWithLifecycle()
    val amplitude by app.amplitude.collectAsStateWithLifecycle()
    val config by app.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sprite = remember(context, app) { CapriSpriteView(context, app.atlas) }
    LaunchedEffect(sprite) { app.avatarCue.collect { sprite.cue(it) } }
    AndroidView(factory = { sprite }, modifier = modifier,
        update = { it.speaking = speaking; it.amplitude = amplitude; it.sleepMinutes = config.sleepMinutes; it.seated = config.seated; it.preview(preview) })
}

@Composable private fun Mainframe(app: CapriApplication, saved: CapriSettings, running: Boolean,
    allowed: Boolean, onShow: () -> Unit, onHide: () -> Unit, onChat: () -> Unit, onChatGptSignIn: (String?) -> Unit, onImportVoice: () -> Unit) {
    var nickname by remember(saved.nickname) { mutableStateOf(saved.nickname) }
    var endpoint by remember(saved.endpoint) { mutableStateOf(saved.endpoint) }
    var model by remember(saved.model) { mutableStateOf(saved.model) }
    var apiKey by remember(saved.apiKey) { mutableStateOf(saved.apiKey) }
    var provider by remember(saved.provider) { mutableStateOf(saved.provider) }
    var voiceMode by remember(saved.voiceMode) { mutableStateOf(saved.voiceMode) }
    var qwenSpeed by remember(saved.qwenSpeed) { mutableFloatStateOf(saved.qwenSpeed) }
    var qwenRobotic by remember(saved.qwenRobotic) { mutableFloatStateOf(saved.qwenRobotic) }
    var avatarSize by remember(saved.sizeDp) { mutableFloatStateOf(saved.sizeDp.toFloat()) }
    var volume by remember(saved.volume) { mutableFloatStateOf(saved.volume) }
    var seated by remember(saved.seated) { mutableStateOf(saved.seated) }
    var sleep by remember(saved.sleepMinutes) { mutableStateOf(saved.sleepMinutes) }
    var personality by remember(saved.personality) { mutableStateOf(saved.personality) }
    fun current() = saved.copy(nickname = nickname.trim(), endpoint = endpoint.trim(), model = model.trim(),
        apiKey = apiKey.trim(), voiceMode = voiceMode,
        sizeDp = avatarSize.toInt(), volume = volume,
        seated = seated, sleepMinutes = sleep, personality = personality.ifBlank { CapriSettings.DEFAULT_PERSONALITY }, provider = provider,
        qwenSpeed = qwenSpeed, qwenRobotic = qwenRobotic)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.fillMaxWidth().height(266.dp).clip(Cut).background(Brush.verticalGradient(listOf(Color(0xFF10212B), Night)))
            .border(1.dp, Edge, Cut)) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width * .52f, size.height * .5f)
                drawCircle(Cyan.copy(alpha = .12f), size.height * .34f, center, style = Stroke(1.dp.toPx()))
                drawCircle(Cyan.copy(alpha = .06f), size.height * .43f, center, style = Stroke(1.dp.toPx()))
                drawLine(Cyan.copy(alpha = .1f), Offset(18.dp.toPx(), size.height - 30.dp.toPx()), Offset(size.width - 18.dp.toPx(), size.height - 30.dp.toPx()))
            }
            Text("CONSTRUCT / 001", color = Muted, fontFamily = Mono, fontSize = 9.sp, modifier = Modifier.padding(14.dp))
            Text(if (running) "● EN TU PANTALLA" else "● EN MAINFRAME", color = Cyan, fontFamily = Mono, fontSize = 9.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp))
            Sprite(app, Modifier.align(Alignment.Center).width(153.dp).height(235.dp))
            Text("TU TELÉFONO. MI NUEVO HOGAR.", color = Ink, fontFamily = Mono, fontSize = 10.sp,
                letterSpacing = 1.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CapriButton(if (running) "OCULTAR CAPRI" else "MOSTRAR CAPRI", if (running) onHide else onShow,
                Modifier.weight(1f), accent = true)
            CapriButton("CONVERSAR", onChat, Modifier.weight(1f))
        }
        Text(if (allowed) "SUPERPOSICIÓN AUTORIZADA / ARRASTRÁ PARA MOVERLA" else "Al mostrarla, Android te pedirá permitir que aparezca sobre otras apps.",
            color = Muted, fontFamily = Mono, fontSize = 11.sp, lineHeight = 16.sp)

        Text("Tocá a Capri para ver lápiz y voz. Mantené pulsado para opciones. Sus mensajes aparecen en un globo temporal.", color = Muted, fontSize = 11.sp)
        Section("01", "CEREBRO", "Conversación en español") {
            ProviderPicker(provider) { provider = it; endpoint = it.defaultEndpoint; apiKey = ""; model = "" }
            if (provider == AiProvider.CHATGPT) {
                ChatGptPanel(app, model, { model = it }, onChatGptSignIn)
            } else {
                Text("Conectá un proveedor de IA o un servidor en tu red local.", color = Muted, fontSize = 12.sp)
                Field("URL del servidor", endpoint, { endpoint = it }, hint = "https://servidor/v1", keyboard = KeyboardType.Uri)
                Field("Modelo", model, { model = it }, hint = "ID del modelo de tu proveedor")
                Field("Clave API", apiKey, { apiKey = it }, secret = true, hint = "Opcional para servidores locales")
                Text("El chat se envía al servidor que configures. Las claves se cifran en este teléfono.", color = Muted, fontSize = 11.sp)
            }
        }
        Section("02", "VOZ", "Escuchame en español") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(VoiceMode.SYSTEM to "ANDROID", VoiceMode.QWEN to "QWEN").forEach { (mode, label) ->
                    Choice(label, voiceMode == mode, { voiceMode = mode }, Modifier.weight(1f))
                }
            }
            when (voiceMode) {
                VoiceMode.SYSTEM -> Text("Usa la voz española instalada en Android. Su timbre depende del motor del teléfono.", color = Muted, fontSize = 12.sp)
                VoiceMode.QWEN -> {
                    QwenPanel(app, onImportVoice)
                    LabelValue("RITMO ALEGRE", "${kotlin.math.round(qwenSpeed * 100).toInt()}%")
                    Slider(value = qwenSpeed, onValueChange = { qwenSpeed = it }, valueRange = 1f..1.2f,
                        colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
                    LabelValue("FILTRO ROBÓTICO SUAVE", "${kotlin.math.round(qwenRobotic / .35f * 100).toInt()}%")
                    Slider(value = qwenRobotic, onValueChange = { qwenRobotic = it }, valueRange = 0f..0.35f,
                        colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
                    Text("Un toque metálico sobre tu voz de referencia. Bajá el filtro a cero para oírla natural. El ritmo también sube un poco el tono; no acelera la generación de Qwen.", color = Muted, fontSize = 11.sp)
                }
                else -> Text("Selecciona Android o Qwen.", color = Muted, fontSize = 12.sp)
            }
            LabelValue("VOLUMEN", "${(volume * 100).toInt()}%")
            Slider(value = volume, onValueChange = { volume = it }, colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CapriButton("PROBAR VOZ", { app.voice.speak("¡Aquí estoy! Tengo una idea para alegrarte el día. ¿Me cuentas qué estás haciendo?", current()) }, Modifier.weight(1f))
                CapriButton("SILENCIAR", { app.voice.stop() }, Modifier.weight(1f))
            }
            Text("Hablar funciona dentro de la app. El botón central de Capri flotante inicia un chat de voz por turnos sin abrir esta pantalla; puedes detenerlo desde la mascota o su notificación. El reconocimiento puede usar internet según el servicio de Android.", color = Muted, fontSize = 11.sp)
        }
        Section("03", "CAPRI", "Haceme un lugar") {
            Field("¿Cómo te llamo?", nickname, { nickname = it.take(40) })
            LabelValue("TAMAÑO FLOTANTE", "${avatarSize.toInt()} dp")
            Slider(value = avatarSize, onValueChange = { avatarSize = it }, valueRange = 100f..240f, steps = 6,
                colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sentarse", color = Ink, modifier = Modifier.weight(1f))
                Switch(checked = seated, onCheckedChange = { seated = it }, colors = SwitchDefaults.colors(checkedThumbColor = Night, checkedTrackColor = Cyan))
            }
            LabelValue("DORMIR TRAS", if (sleep == 0) "NUNCA" else "$sleep MIN")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 5, 15).forEach { minutes -> Choice(if (minutes == 0) "Nunca" else "$minutes min", sleep == minutes,
                    { sleep = minutes }, Modifier.weight(1f)) }
            }
            Text("Personalidad base: compañera curiosa y juguetona, español latino, respuestas breves y sin apodos. Este contexto adicional se aplica a sus conversaciones y comentarios.", color = Muted, fontSize = 11.sp)
            Field("Contexto y comportamiento", personality, { personality = it.take(8000) }, singleLine = false)
            TextButton(onClick = { personality = CapriSettings.DEFAULT_PERSONALITY }) { Text("Restaurar personalidad", color = Muted) }
        }
        CapriButton("GUARDAR AJUSTES", {
            runCatching { com.capri.companion.data.EndpointPolicy.chatUrl(endpoint) }
                .onSuccess { app.save(current()) }.onFailure { app.notice.value = it.message }
        }, Modifier.fillMaxWidth(), accent = true)
        Text("CAPRI ANDROID / ${BuildConfig.VERSION_NAME}\nBasada en Capri Desktop de Lachy. Proyecto de fans no oficial.\nAndroid ${Build.VERSION.RELEASE} · Español", color = Muted,
            fontFamily = Mono, fontSize = 10.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun Conversation(app: CapriApplication, partial: String, onMicrophone: () -> Unit) {
    val messages by app.messages.collectAsStateWithLifecycle()
    val busy by app.busy.collectAsStateWithLifecycle()
    val speaking by app.speaking.collectAsStateWithLifecycle()
    val preparing by app.voice.preparing.collectAsStateWithLifecycle()
    val listening by app.listening.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var clear by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { app.composing.value = false } }
    LaunchedEffect(draft) { app.composing.value = draft.isNotBlank(); if (draft.isNotBlank()) app.contextual.userInteracted() }
    val list = rememberLazyListState()
    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Sprite(app, Modifier.width(49.dp).height(72.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("CANAL / CAPRI", color = Red, fontFamily = Mono, fontSize = 15.sp)
                Text(when { listening -> "TE ESCUCHO…"; busy -> "PENSANDO…"; preparing -> "PREPARANDO VOZ…"; speaking -> "HABLANDO…"; else -> "ACÁ ESTOY" }, color = Cyan, fontFamily = Mono, fontSize = 10.sp)
            }
            TextButton(onClick = { clear = true }) { Text("Borrar", color = Muted, fontSize = 11.sp) }
        }
        HorizontalDivider(color = Edge)
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("NUEVA CONEXIÓN", fontFamily = Mono, color = Cyan, fontSize = 10.sp, letterSpacing = 3.sp)
                Spacer(Modifier.height(12.dp))
                Text("Bueno, ya estoy acá.", color = Ink, fontFamily = Mono, fontSize = 22.sp)
                Spacer(Modifier.height(8.dp))
                Text(if (settings.model.isBlank()) "Conectá mi cerebro en Mainframe\ny contame algo de vos." else "Contame cómo estuvo tu día.\nPrometo portarme… más o menos.",
                    color = Muted, fontFamily = Mono, fontSize = 13.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(22.dp))
                CapriButton("¿QUIÉN SOS?", { app.send("¿Quién sos, Capri?") })
            }
        } else {
            LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(messages, key = { it.id }) { message ->
                    val user = message.role == "user"
                    Column(Modifier.fillMaxWidth().padding(start = if (user) 25.dp else 0.dp, end = if (user) 0.dp else 16.dp)
                        .border(1.dp, if (user) Edge else Cyan.copy(alpha = .35f), Cut).background(Panel.copy(alpha = .8f), Cut).padding(14.dp)) {
                        Text(if (user) "VOS" else "CAPRI", color = if (user) Muted else Red, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(message.text, color = if (user) Ink else Color(0xFFAAEDF5), fontFamily = Mono, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Cyan, trackColor = Panel) }
            }
        }
        if (listening) Text(partial.ifBlank { "Te escucho…" }, color = Yellow, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = { app.contextual.userInteracted(); draft = it.take(4000) }, modifier = Modifier.weight(1f),
                placeholder = { Text("Cuéntame…", color = Muted, fontSize = 13.sp) }, maxLines = 4, shape = Cut,
                textStyle = TextStyle(fontFamily = Mono, color = Ink, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!busy && draft.isNotBlank()) { app.send(draft); if (app.settings.value.model.isNotBlank()) draft = "" } }),
                colors = fieldColors())
            CapriButton("↑", {
                if (draft.isNotBlank()) { app.send(draft); if (app.settings.value.model.isNotBlank()) draft = "" }
            }, Modifier.width(52.dp), accent = true, enabled = !busy && draft.isNotBlank())
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CapriButton(if (listening) "CERRAR MIC" else "HABLAR", onMicrophone, Modifier.weight(1f), accent = listening, enabled = !busy)
            CapriButton(if (busy || speaking || preparing) "DETENER" else "REPETIR VOZ", { if (busy || speaking || preparing) app.cancel() else app.replay() }, Modifier.weight(1f),
                enabled = busy || speaking || preparing || messages.any { it.role == "assistant" })
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("¿Borrar conversación?") },
        text = { Text("Se eliminará el historial guardado en este teléfono.") },
        confirmButton = { TextButton(onClick = { app.clearConversation(); clear = false }) { Text("Borrar", color = Red) } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancelar") } })
}

@Composable private fun Archive(app: CapriApplication) {
    var preview by remember { mutableStateOf(Pose.IDLE) }
    val context = LocalContext.current
    var license by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Section("//", "ARCHIVO VISUAL", "Animaciones originales de Capri Desktop") {
            Box(Modifier.fillMaxWidth().height(280.dp).background(Brush.radialGradient(listOf(Color(0xFF12303A), Night))), contentAlignment = Alignment.Center) {
                Sprite(app, Modifier.size(230.dp, 270.dp), preview)
            }
            Text("${preview.asset.uppercase()} / ${preview.frames} CUADROS / ${preview.fps} FPS", fontFamily = Mono, color = Cyan, fontSize = 11.sp)
            val choices = listOf(Pose.IDLE to "Reposo", Pose.BLINK to "Parpadeo", Pose.GRIN to "Sonrisa", Pose.GLITCH to "Glitch",
                Pose.DRAG to "Arrastre", Pose.SLEEP to "Sueño", Pose.WAKE to "Despertar", Pose.PERCH to "Sentada",
                Pose.ARRIVE to "Llegada", Pose.LEAVE to "Salida", Pose.WALK to "Caminar", Pose.DANCE to "Baile")
            choices.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (pose, label) -> Choice(label, preview == pose, { preview = pose }, Modifier.weight(1f)) }
                }
            }
        }
        TextButton(onClick = { license = true }) { Text("Licencias", color = Muted, fontSize = 11.sp) }
    }
    if (license) {
        val text = remember { listOf("CAPRI_LICENSE.md", "OFL-ShareTechMono.txt", "QWEN_ANDROID_LICENSE.txt",
            "QWEN_NATIVE_LICENSE.txt", "GGML_LICENSE.txt", "QWEN_MODEL_APACHE_2.txt").joinToString("\n\n---\n\n") { name ->
                name + "\n\n" + context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
            } }
        AlertDialog(onDismissRequest = { license = false }, title = { Text("Licencias") },
            text = { Text(text, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), fontSize = 11.sp) },
            confirmButton = { TextButton(onClick = { license = false }) { Text("Cerrar") } })
    }
}

@Composable private fun ProviderPicker(selected: AiProvider, onSelect: (AiProvider) -> Unit) {
    AiProvider.entries.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { Choice(it.label, selected == it, { onSelect(it) }, Modifier.weight(1f)) }
        }
    }
}

@Composable private fun ChatGptPanel(app: CapriApplication, model: String, onModel: (String) -> Unit,
    onSignIn: (String?) -> Unit) {
    val state by app.chatgpt.state.collectAsStateWithLifecycle()
    val busy by app.busy.collectAsStateWithLifecycle()
    val links = androidx.compose.ui.platform.LocalUriHandler.current
    var accountsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.activeId, state.authorizing) {
        if (state.ready && !state.authorizing && !state.working && state.models.isEmpty()) app.chatgpt.loadModels()
    }
    Text("USAR MI PLAN CHATGPT", color = Cyan, fontSize = 12.sp)
    Text("Conectá una cuenta Plus o Pro elegible. Autorizarás el uso de tu plan para Capri en el navegador. Las conversaciones y capturas usan sus límites; no se garantiza uso ilimitado.", color = Muted, fontSize = 12.sp)
    Text(state.status, color = Yellow, fontSize = 12.sp, modifier = Modifier.testTag("chatgpt-status"))
    state.active?.let { Text(it.label, color = Ink, fontSize = 12.sp) }
    if (state.authorizing || state.working) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
    CapriButton("Continue with ChatGPT", { onSignIn(state.activeId.takeIf { it.isNotBlank() }) },
        Modifier.fillMaxWidth().testTag("chatgpt-sign-in"), accent = true, enabled = !state.authorizing && !state.working && !busy)
    if (state.authorizing) CapriButton("CANCELAR INICIO DE SESIÓN", { app.chatgpt.cancelSignIn() }, Modifier.fillMaxWidth())
    if (state.accounts.isNotEmpty()) {
        Box {
            CapriButton("ELEGIR CUENTA", { accountsOpen = true }, Modifier.fillMaxWidth(), enabled = !state.authorizing && !state.working && !busy)
            DropdownMenu(expanded = accountsOpen, onDismissRequest = { accountsOpen = false }) {
                state.accounts.forEach { account ->
                    DropdownMenuItem(text = { Text(account.label + if (account.id == state.activeId) " ✓" else "", fontSize = 12.sp) }, onClick = {
                        accountsOpen = false; app.stopForAccountChange(); onModel("")
                        if (account.connected) app.chatgpt.selectAccount(account.id) else onSignIn(account.id)
                    })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CapriButton("AGREGAR CUENTA", { onSignIn(null) }, Modifier.weight(1f), enabled = !state.authorizing && !state.working && !busy)
            CapriButton("CERRAR SESIÓN", { app.stopForAccountChange(); app.chatgpt.signOut(); onModel("") }, Modifier.weight(1f),
                enabled = state.active?.connected == true && !state.authorizing && !state.working)
        }
    }
    ChatGptModelPicker(app, model, onModel)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CapriButton("ACTUALIZAR MODELOS", { app.chatgpt.loadModels() }, Modifier.weight(1f), enabled = state.ready && !state.authorizing && !state.working && !busy)
        CapriButton(if (busy) "DETENER PRUEBA" else "PROBAR CONEXIÓN", { if (busy) app.cancel() else app.testChatGpt(model) }, Modifier.weight(1f),
            enabled = busy || state.ready && state.models.any { it.id == model } && !state.authorizing && !state.working)
    }
    TextButton(onClick = { app.stopForAccountChange(); links.openUri("https://chatgpt.com/settings/usage") }) {
        Text("Ver uso y permisos en ChatGPT", color = Cyan, fontSize = 12.sp)
    }
    Text("Guardá los ajustes después de elegir el modelo. El inicio de sesión no importa tu historial de ChatGPT. Capri cifra la sesión en este teléfono. Abrir la conexión detiene la observación de pantalla.", color = Muted, fontSize = 11.sp)
}

@Composable private fun ChatGptModelPicker(app: CapriApplication, model: String, onModel: (String) -> Unit) {
    val state by app.chatgpt.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    Box {
        CapriButton(state.models.firstOrNull { it.id == model }?.label ?: if (model.isBlank()) "ELEGIR MODELO CHATGPT" else "$model · actualizar lista",
            { open = true }, Modifier.fillMaxWidth().testTag("chatgpt-model"), enabled = state.models.isNotEmpty() && !state.authorizing && !state.working)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            state.models.forEach { item ->
                DropdownMenuItem(text = { Text(item.label, fontSize = 13.sp) }, onClick = { onModel(item.id); open = false })
            }
        }
    }
    if (model.isNotBlank()) Text("Modelo: $model", color = Muted, fontSize = 11.sp)
}

@Composable private fun QwenPanel(app: CapriApplication, onImportVoice: () -> Unit) {
    val state by app.qwen.state.collectAsStateWithLifecycle()
    val metrics by app.qwen.metrics.collectAsStateWithLifecycle()
    Text("Qwen · voz de Capri en español latino", color = Cyan, fontSize = 12.sp)
    Text("Voz y perfil preconfigurados. La edición completa incluye los modelos; no necesitas cargar archivos ni contratar una API de voz.", color = Muted, fontSize = 11.sp)
    Text(state.status, color = Yellow, fontSize = 12.sp)
    if (state.working && !state.installed) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), color = Cyan)
    if (metrics.cached) Text("Respuesta disponible en memoria para repetir.", color = Cyan, fontSize = 11.sp)
    else if (metrics.firstAudioMs > 0) Text("Preparación: ${"%.1f".format(metrics.firstAudioMs / 1000.0)} s", color = Muted, fontSize = 11.sp)
    CapriButton("PREPARAR VOZ", { app.qwen.downloadModels() }, Modifier.fillMaxWidth(), enabled = state.installed && !state.working)
}

@Composable private fun Environment(app: CapriApplication, onShare: () -> Unit, onStop: () -> Unit, onShow: () -> Unit) {
    LaunchedEffect(Unit) { app.contextual.refreshMemories() }
    val saved by app.settings.collectAsStateWithLifecycle()
    val state by app.contextual.state.collectAsStateWithLifecycle()
    val observation by app.contextual.snapshot.collectAsStateWithLifecycle()
    val memories by app.contextual.memories.collectAsStateWithLifecycle()
    val floating by app.overlayRunning.collectAsStateWithLifecycle()
    var enabled by remember(saved.visionEnabled) { mutableStateOf(saved.visionEnabled) }
    var useBrain by remember(saved.visionUseBrain) { mutableStateOf(saved.visionUseBrain) }
    var provider by remember(saved.visionProvider) { mutableStateOf(saved.visionProvider) }
    var endpoint by remember(saved.visionEndpoint) { mutableStateOf(saved.visionEndpoint) }
    var model by remember(saved.visionModel) { mutableStateOf(saved.visionModel) }
    var key by remember(saved.visionApiKey) { mutableStateOf(saved.visionApiKey) }
    var auto by remember(saved.autoComments) { mutableStateOf(saved.autoComments) }
    var rememberActivities by remember(saved.rememberActivities) { mutableStateOf(saved.rememberActivities) }
    var consent by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Section("04", "ENTORNO", "Compañía con contexto de pantalla") {
            Text(state.status, color = if (state.active) Cyan else Muted, fontSize = 13.sp)
            Text(if (com.capri.companion.BuildConfig.MANUAL_CAPTURE) "Elige cuándo compartir una sola imagen. Android te pedirá autorización en cada captura; se enviará al proveedor de IA elegido y la captura se cerrará al obtenerla." else "Capri puede obtener una captura puntual de la pantalla visible cada tres minutos. Cada imagen se envía al proveedor de IA elegido. No mantiene un flujo de video ni graba audio.", color = Muted, fontSize = 12.sp)
            if (!floating) CapriButton("MOSTRAR CAPRI FLOTANTE", onShow, Modifier.fillMaxWidth())
            if (state.analyzing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan)
            if (com.capri.companion.BuildConfig.MANUAL_CAPTURE) {
                CapriButton("TOMAR CAPTURA", onShare, Modifier.fillMaxWidth(), accent = true, enabled = !state.analyzing)
                if (state.active) CapriButton("OLVIDAR CAPTURA", onStop, Modifier.fillMaxWidth())
                Text("También puedes tomarla desde el menú de Capri flotante. Android 14+ permite elegir una sola app. No hay capturas automáticas ni permiso de Accesibilidad.", color = Muted, fontSize = 11.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CapriButton(if (state.active) "DETENER CAPTURAS" else "ACTIVAR CAPTURAS PUNTUALES", {
                        if (state.active) onStop() else consent = true
                    }, Modifier.weight(1f), accent = true, enabled = state.active || Build.VERSION.SDK_INT >= 30)
                }
                CapriButton("MIRÁ ESTO", { app.contextual.lookNow() }, Modifier.fillMaxWidth(), enabled = state.active && state.visible && !state.analyzing)
                Text("Android 11+ y permiso de capturas de Accesibilidad. Captura la pantalla visible completa, salvo superficies protegidas. Al bloquear el teléfono se detiene la sesión. Detener capturas corta los envíos; pausar comentarios sólo silencia las reacciones.", color = Muted, fontSize = 11.sp)
            }
            observation?.let {
                Text("OBSERVACIÓN RECIENTE", color = Yellow, fontSize = 11.sp)
                Text(it.result.observation, color = Ink, fontSize = 13.sp)
                Text("${java.time.Instant.ofEpochMilli(it.capturedAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0)} · Confianza ${it.result.confidence} · Caduca en 2 min", color = Muted, fontSize = 10.sp)
            }
        }
        Section("05", "VISIÓN", "Elegí un modelo que admita imágenes") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Habilitar análisis visual", Modifier.weight(1f), color = Ink)
                Switch(enabled, { enabled = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Usar la conexión del cerebro", Modifier.weight(1f), color = Ink, fontSize = 13.sp)
                Switch(useBrain, { useBrain = it })
            }
            if (!useBrain) {
                ProviderPicker(provider) { provider = it; endpoint = it.defaultEndpoint; key = ""; model = "" }
                if (provider != AiProvider.CHATGPT) {
                    Field("URL del proveedor visual", endpoint, { endpoint = it }, keyboard = KeyboardType.Uri)
                    Field("Clave de visión", key, { key = it }, secret = true)
                } else Text("Usa la cuenta conectada en Mainframe. Elegí un modelo de esa cuenta que admita imágenes.", color = Muted, fontSize = 11.sp)
            } else Text("Conexión actual: ${saved.provider.label}. El modelo elegido debe admitir imágenes.", color = Muted, fontSize = 11.sp)
            if (!useBrain && provider == AiProvider.CHATGPT) ChatGptModelPicker(app, model, { model = it })
            else if (useBrain && saved.provider == AiProvider.CHATGPT) {
                Text("Modelo visual: ${model.ifBlank { saved.model }}. Dejá el mismo modelo del cerebro si admite imágenes.", color = Muted, fontSize = 12.sp)
                ChatGptModelPicker(app, model.ifBlank { saved.model }, { model = it })
                TextButton(onClick = { model = "" }) { Text("Usar siempre el modelo del cerebro", color = Cyan, fontSize = 11.sp) }
            } else Field("Modelo visual", model, { model = it }, hint = if (useBrain) "Vacío: usar el modelo del cerebro" else "ID de un modelo con visión")
        }
        Section("06", "INICIATIVA", "Cuándo me meto en la conversación") {
            if (!com.capri.companion.BuildConfig.MANUAL_CAPTURE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Comentarios cada 3 minutos", Modifier.weight(1f), color = Ink)
                    Switch(auto, { auto = it })
                }
                Text("Durante la sesión autorizada se envía una captura al comenzar y luego cada 3 minutos, aunque la pantalla siga igual. Capri comenta si el contenido es claro y apropiado. No interrumpe tu voz ni una respuesta en curso. Máximo 20 comentarios por hora; no repite la misma frase.", color = Muted, fontSize = 11.sp)
            } else Text("Capri comenta únicamente las imágenes que pidas capturar. No observa la pantalla entre solicitudes ni inicia capturas por su cuenta.", color = Muted, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recordar resúmenes de actividades", Modifier.weight(1f), color = Ink, fontSize = 13.sp)
                Switch(rememberActivities, { rememberActivities = it })
            }
            Text("Opcional: guardar resúmenes de sesiones observadas en este teléfono durante 7 días. No se guardan imágenes. Podés borrarlos abajo.", color = Muted, fontSize = 11.sp)
        }
        CapriButton("GUARDAR ENTORNO", {
            runCatching { com.capri.companion.data.EndpointPolicy.chatUrl(if (useBrain) saved.endpoint else endpoint) }
                .onSuccess { app.save(saved.copy(visionEnabled = enabled, visionUseBrain = useBrain, visionProvider = provider,
                    visionEndpoint = endpoint.trim(), visionModel = model.trim(), visionApiKey = key.trim(), autoComments = auto,
                    rememberActivities = rememberActivities)) }.onFailure { app.notice.value = it.message }
        }, Modifier.fillMaxWidth(), accent = true)
        Section("07", "LO QUE RECUERDO", "Sólo sesiones que compartiste") {
            if (memories.isEmpty()) Text("No hay actividades guardadas.", color = Muted, fontSize = 12.sp)
            memories.asReversed().take(30).forEach {
                Text(java.time.Instant.ofEpochMilli(it.at).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString(), color = Yellow, fontSize = 10.sp)
                Text(it.summary, color = Ink, fontSize = 12.sp)
                HorizontalDivider(color = Edge)
            }
            CapriButton("BORRAR RECUERDOS", { erase = true }, Modifier.fillMaxWidth(), enabled = memories.isNotEmpty())
        }
    }
    if (consent) {
        val providerName = if (saved.visionUseBrain) saved.provider.label else saved.visionProvider.label
        val server = runCatching { java.net.URI(if (saved.visionUseBrain) saved.endpoint else saved.visionEndpoint).host }.getOrNull().orEmpty()
        AlertDialog(onDismissRequest = { consent = false }, title = { Text("Capturas puntuales de pantalla") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Al salir de Capri se obtendrá una captura y después una cada 3 minutos al proveedor $providerName ($server) para entender lo visible y reaccionar. Las imágenes salen del teléfono; su tratamiento depende del proveedor. No se guardan capturas en Capri.")
                Spacer(Modifier.height(12.dp))
                Text("Usa la función de captura de Accesibilidad: debes habilitarla en Ajustes de Android y volver aquí para iniciar. Captura toda la pantalla visible, incluidas otras apps y notificaciones; no lee controles ni pulsa botones. No obtiene imágenes mientras estás en Capri. Puedes detenerlo desde Entorno o la notificación, y revocar el permiso en Ajustes.")
                Spacer(Modifier.height(12.dp))
                Text(if (saved.rememberActivities) "Activaste guardar resúmenes por 7 días en este teléfono." else "Los resúmenes de actividad no se guardarán de forma permanente.")
            } },
            confirmButton = { TextButton(onClick = { consent = false; onShare() }) { Text("Configurar e iniciar") } },
            dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancelar") } })
    }
    if (erase) AlertDialog(onDismissRequest = { erase = false }, title = { Text("¿Borrar recuerdos?") },
        text = { Text("Se eliminarán los resúmenes guardados en este teléfono.") },
        confirmButton = { TextButton(onClick = { app.contextual.eraseMemories(); erase = false }) { Text("Borrar", color = Red) } },
        dismissButton = { TextButton(onClick = { erase = false }) { Text("Cancelar") } })
}

@Composable private fun Section(number: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, Edge, Cut).background(Panel.copy(alpha = .7f), Cut)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(28.dp).background(Red))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("$title >", color = Yellow, fontFamily = Mono, fontSize = 14.sp, letterSpacing = 2.sp)
                Text(subtitle, color = Muted, fontFamily = Mono, fontSize = 10.sp)
            }
            Text(number, color = Edge, fontFamily = Mono, fontSize = 23.sp)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable private fun CapriButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = Cut,
        border = BorderStroke(1.dp, if (accent && enabled) Cyan else Edge), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (accent) Cyan.copy(alpha = .08f) else Panel,
            contentColor = if (accent) Cyan else Ink, disabledContentColor = Muted.copy(alpha = .5f))) {
        Text(text, fontFamily = Mono, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun Choice(text: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = Cut,
        border = BorderStroke(1.dp, if (active) Cyan else Edge), contentPadding = PaddingValues(horizontal = 3.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active) Cyan.copy(alpha = .12f) else Night, contentColor = if (active) Cyan else Muted)) {
        Text(text, fontFamily = Mono, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun Field(label: String, value: String, onChange: (String) -> Unit, secret: Boolean = false,
    hint: String = "", keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 12.sp) }, placeholder = { Text(hint, color = Muted, fontSize = 12.sp) },
        singleLine = singleLine, minLines = if (singleLine) 1 else 4, maxLines = if (singleLine) 1 else 8,
        shape = Cut, colors = fieldColors(), textStyle = TextStyle(fontFamily = Mono, color = Ink, fontSize = 13.sp),
        visualTransformation = if (secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard,
            autoCorrectEnabled = !secret && keyboard == KeyboardType.Text),
        trailingIcon = if (secret) ({ TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Ocultar" else "Ver", fontSize = 10.sp) } }) else null)
}

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = Edge,
    focusedLabelColor = Cyan, unfocusedLabelColor = Muted, cursorColor = Cyan, focusedContainerColor = Night, unfocusedContainerColor = Night)

@Composable private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontFamily = Mono, fontSize = 11.sp)
        Text(value, color = Cyan, fontFamily = Mono, fontSize = 11.sp)
    }
}
