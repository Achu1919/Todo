package dev.saikrishna.todo.ui.capture

import dev.saikrishna.todo.ui.components.horizontalEdgeFade
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.domain.ShareCapture
import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.PillChip
import dev.saikrishna.todo.ui.components.glassSurface
import dev.saikrishna.todo.domain.ComposerDraft
import dev.saikrishna.todo.ui.home.ComposerToolRow
import dev.saikrishna.todo.ui.home.DraftChips
import dev.saikrishna.todo.ui.home.ToolPopover
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** How long the "Added" confirmation stays before the card leaves. */
const val CaptureConfirmMs = 650L

/**
 * The floating capture card. Enter: the scrim fades in and the card rises 48 dp into place on
 * [Springs.Glide]; leave (cancel / after the confirm): it sinks and fades on [Springs.Quick].
 * Reduced motion: 150 ms fades, no movement.
 */
@Composable
fun CaptureScreen(
    draft: ShareCapture.Draft,
    lists: List<TaskList>,
    defaultList: Long,
    windowBlur: Boolean,
    onSave: (parsed: QuickParse.Result, notes: String, listId: Long) -> Unit,
    onFinished: () -> Unit,
    clock: () -> LocalDateTime = LocalDateTime::now,
    autoFocus: Boolean = true,
    /** v0.12 widget presets: list, due today, start listening. */
    preset: CapturePreset = CapturePreset(),
    /** Test seam: voice input (null = the real recogniser). */
    voiceOverride: VoiceControl? = null,
    /** v0.13: the toolbar's merged draft (reminder lead and toolbar notes included). Null = [onSave]. */
    onSaveDraft: ((draft: ComposerDraft.Draft, notes: String, listId: Long) -> Unit)? = null,
) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val title = rememberTextFieldState(draft.title)
    val notes = rememberTextFieldState(draft.notes)
    var ignored by remember { mutableStateOf(emptySet<String>()) }
    // A preset list wins over the focus default while it exists.
    val startList = preset.listId?.takeIf { id -> lists.any { it.id == id } } ?: defaultList
    var chosenList by remember { mutableLongStateOf(startList) }
    var listTouched by remember { mutableStateOf(false) }
    if (!listTouched) chosenList = startList
    var dueToday by remember { mutableStateOf(preset.dueToday) }
    // v0.13 add-task toolbar (same tools as the composer).
    var picks by remember { mutableStateOf(ComposerDraft.Picks()) }
    var activeTool by remember { mutableStateOf<ComposerDraft.Tool?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val voice = voiceOverride ?: remember { RealVoice(dev.saikrishna.todo.ui.home.VoiceInput(context.applicationContext)) }
    androidx.compose.runtime.DisposableEffect(voice) { onDispose { voice.cancel() } }
    var voiceHint by remember { mutableStateOf<String?>(null) }
    fun startVoice() {
        voiceHint = null
        keyboard?.hide()
        val prefix = title.text.toString().trimEnd().let { if (it.isEmpty()) "" else "$it " }
        voice.start(
            onText = { heard -> title.edit { replace(0, length, prefix + heard); selection = androidx.compose.ui.text.TextRange(length) } },
            onDone = { err -> voiceHint = err },
        )
    }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startVoice() else voiceHint = "Microphone permission is off" }
    fun toggleVoice() {
        haptics.tick()
        when {
            voice.listening -> voice.stop()
            !voice.available -> voiceHint = "Voice input isn't available on this phone"
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED -> startVoice()
            else -> micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
    var savedLabel by remember { mutableStateOf<String?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val appear = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    val rise = with(LocalDensity.current) { 48.dp.toPx() }

    LaunchedEffect(Unit) {
        if (preset.voice) toggleVoice() else if (autoFocus) focus.requestFocus()
        appear.animateTo(1f, if (reduced) Tweens.reduced() else Springs.Glide.float)
    }

    fun leave() {
        if (leaving) return
        leaving = true
        keyboard?.hide()
        scope.launch {
            appear.animateTo(0f, if (reduced) Tweens.reduced() else Springs.Quick.float)
            onFinished()
        }
    }

    val raw = QuickParse.parse(title.text.toString(), clock(), ignored, lists)
    // "Due today" preset: today unless the text names a day.
    val parsed = applyDueToday(raw, dueToday, clock().toLocalDate())
    val draftNow = ComposerDraft.merge(parsed, picks, clock(), lists)
    val listId = draftNow.listId ?: chosenList

    fun save() {
        if (parsed.title.isBlank() || savedLabel != null) return
        if (voice.listening) voice.stop()
        haptics.thump()
        val d = draftNow
        val toDraft = onSaveDraft
        if (toDraft != null) toDraft(d, notes.text.toString(), listId)
        else onSave(
            parsed.copy(dueDay = d.dueDay, dueMinute = d.dueMinute, priority = d.priority, repeat = d.repeat),
            listOf(notes.text.toString(), d.notes).filter { it.isNotBlank() }.joinToString("\n\n"),
            listId,
        )
        val today = clock().toLocalDate()
        val day = d.dueDay
        savedLabel = when {
            day == null || day == today -> "Added to Today"
            day.isBefore(today) -> "Added to Today"
            else -> "Added for ${QuickParse.dateLabel(day, today)}"
        }
        keyboard?.hide()
        scope.launch { delay(CaptureConfirmMs); leave() }
    }

    BackHandler { leave() }

    Box(Modifier.fillMaxSize()) {
        // Scrim: dims (and, with window blur, just softens) what is behind. Tap = cancel.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = (if (windowBlur) 0.16f else 0.34f) * appear.value.coerceIn(0f, 1f))) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Cancel") { leave() }
                .semantics { contentDescription = "Close" },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = if (reduced) 0f else (1f - p) * rise
                }
                .testTag("capture-card")
                .glassSurface(Shapes.xl, GlassLevel.High, if (windowBlur) c.glassTint else c.paperRaised)
                .clip(Shapes.xl),
        ) {
            AnimatedContent(
                targetState = savedLabel,
                transitionSpec = {
                    (if (reduced) fadeIn(Tweens.reduced()) else fadeIn(Tweens.fadeIn(60))) togetherWith fadeOut(Tweens.fadeOutFast()) using
                        SizeTransform(clip = true) { _, _ -> if (reduced) tween(150) else Springs.Smooth.intSize }
                },
                label = "capture content",
            ) { label ->
                if (label != null) Confirm(label)
                else Form(
                    title = title, notes = notes, parsed = parsed, lists = lists, listId = listId,
                    draft = draftNow,
                    picks = picks,
                    activeTool = activeTool,
                    today = clock().toLocalDate(),
                    onPicks = { picks = it },
                    onTool = { t -> haptics.tick(); activeTool = if (activeTool == t) null else t },
                    onClearChip = { chip ->
                        haptics.tick()
                        if (chip.tool == ComposerDraft.Tool.Date && dueToday && picks.day == null) dueToday = false
                        val (p, ig) = ComposerDraft.clear(chip, picks, ignored)
                        picks = p; ignored = ig
                    },
                    focus = focus,
                    dueToday = dueToday,
                    onDueToday = { haptics.tick(); dueToday = !dueToday },
                    listening = voice.listening,
                    voiceAvailable = voice.available,
                    voiceHint = voiceHint,
                    onVoice = { toggleVoice() },
                    onRemoveToken = { t -> haptics.tick(); ignored = ignored + t.key },
                    onList = { id -> haptics.tick(); listTouched = true; chosenList = id; picks = picks.copy(listId = null); ignored = ignored + parsed.tokens.filter { it.kind == QuickParse.Kind.List }.map { it.key } },
                    onCancel = { leave() },
                    onSave = { save() },
                )
            }
        }
    }
}

@Composable
private fun Form(
    title: TextFieldState,
    notes: TextFieldState,
    parsed: QuickParse.Result,
    lists: List<TaskList>,
    listId: Long,
    draft: ComposerDraft.Draft,
    picks: ComposerDraft.Picks,
    activeTool: ComposerDraft.Tool?,
    today: java.time.LocalDate,
    onPicks: (ComposerDraft.Picks) -> Unit,
    onTool: (ComposerDraft.Tool) -> Unit,
    onClearChip: (ComposerDraft.Chip) -> Unit,
    focus: FocusRequester,
    dueToday: Boolean,
    onDueToday: () -> Unit,
    listening: Boolean,
    voiceAvailable: Boolean,
    voiceHint: String?,
    onVoice: () -> Unit,
    onRemoveToken: (QuickParse.Token) -> Unit,
    onList: (Long) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val c = AppTheme.colors
    val type = AppTheme.type
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
        val header = when {
            listening -> "Listening…"
            voiceHint != null -> voiceHint
            dueToday && parsed.tokens.none { it.kind == QuickParse.Kind.Date } -> "New task · Today"
            else -> "New task"
        }
        Text(
            header, style = type.meta, color = if (listening) c.accent else c.inkSecondary,
            modifier = Modifier.padding(horizontal = 20.dp).testTag("capture-header"),
        )
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            state = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .focusRequester(focus)
                .semantics { contentDescription = "Task title" },
            textStyle = type.title.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            onKeyboardAction = { onSave() },
            decorator = { inner ->
                Box {
                    if (title.text.isEmpty()) Text("What needs doing?", style = type.title, color = c.inkTertiary)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        // v0.13: the toolbar's chips (typed or picked; tap to change, x to clear) and its pickers.
        DraftChips(
            draft = draft,
            tagTokens = parsed.tokens.filter { it.kind == QuickParse.Kind.Tag },
            lists = lists,
            onOpen = onTool,
            onClear = onClearChip,
            onRemoveTag = onRemoveToken,
            inset = 20.dp,
        )
        ToolPopover(
            tool = activeTool, draft = draft, picks = picks, today = today, lists = lists,
            onPicks = onPicks, onClose = { activeTool?.let(onTool) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        BasicTextField(
            state = notes,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).semantics { contentDescription = "Notes" },
            textStyle = type.meta.copy(color = c.inkSecondary),
            cursorBrush = SolidColor(c.accent),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
            decorator = { inner ->
                Box {
                    if (notes.text.isEmpty()) Text("Notes or a link", style = type.meta, color = c.inkTertiary)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().let { m -> val s = rememberScrollState(); m.horizontalEdgeFade(s).horizontalScroll(s) }.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // v0.12: "Today" (on from the due-today preset; tap to drop it) before the lists.
            if (parsed.tokens.none { it.kind == QuickParse.Kind.Date }) {
                PillChip(label = "Today", selected = dueToday, onClick = onDueToday, modifier = Modifier.testTag("capture-today"))
                Spacer(Modifier.width(4.dp))
            }
            for (l in lists) {
                PillChip(
                    label = listOfNotNull(l.emoji, l.name).joinToString(" "),
                    selected = l.id == listId,
                    onClick = { onList(l.id) },
                )
            }
        }
        ComposerToolRow(visible = true, draft = draft, active = activeTool, onTool = onTool, inset = 20.dp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cancel", style = type.label, color = c.inkSecondary,
                modifier = Modifier.clip(Shapes.full).clickable(role = Role.Button, onClick = onCancel).padding(horizontal = 12.dp, vertical = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            if (voiceAvailable) {
                val micFill by dev.saikrishna.todo.ui.components.animateStateColor(listening, c.accent.copy(alpha = 0.16f), c.inkFill, Tweens.select(), label = "mic fill")
                val micTint by dev.saikrishna.todo.ui.components.animateStateColor(listening, c.accent, c.ink, Tweens.select(), label = "mic tint")
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(Shapes.full)
                        .drawBehind { drawRect(micFill) }
                        .clickable(role = Role.Button, onClickLabel = if (listening) "Stop listening" else "Speak the task", onClick = onVoice)
                        .testTag("capture-voice"),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Mic, null, tint = micTint, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(8.dp))
            }
            val enabled = parsed.title.isNotBlank()
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(Shapes.full)
                    .background(if (enabled) c.ink else c.inkFill)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Save task", onClick = onSave)
                    .padding(horizontal = 22.dp)
                    .testTag("capture-save"),
                contentAlignment = Alignment.Center,
            ) { Text("Save", style = type.label, color = if (enabled) c.onInk else c.inkTertiary) }
        }
    }
}

/** The subtle confirm: a check in a soft accent circle and where the task went. */
@Composable
private fun Confirm(label: String) {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val pop = remember { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(Unit) { if (!reduced) pop.animateTo(1f, Springs.accent(reduced).float) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp).testTag("capture-confirm"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(Shapes.full).background(c.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Check, null, tint = c.accent, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(label, style = AppTheme.type.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Pure: the "due today" preset sets today when the text names no day (a named day wins). */
internal fun applyDueToday(parsed: QuickParse.Result, dueToday: Boolean, today: java.time.LocalDate): QuickParse.Result =
    if (!dueToday || parsed.dueDay != null) parsed else parsed.copy(dueDay = today)

/** Voice seam for the capture card (the real [dev.saikrishna.todo.ui.home.VoiceInput], or a test fake). */
interface VoiceControl {
    val listening: Boolean
    val available: Boolean
    fun start(onText: (String) -> Unit, onDone: (String?) -> Unit)
    fun stop()
    fun cancel()
}

private class RealVoice(private val v: dev.saikrishna.todo.ui.home.VoiceInput) : VoiceControl {
    override val listening: Boolean get() = v.listening
    override val available: Boolean get() = v.available
    override fun start(onText: (String) -> Unit, onDone: (String?) -> Unit) = v.start(onText, onDone)
    override fun stop() = v.stop()
    override fun cancel() = v.cancel()
}
