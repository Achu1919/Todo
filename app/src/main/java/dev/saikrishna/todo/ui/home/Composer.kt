package dev.saikrishna.todo.ui.home

import android.Manifest
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.content.ContextCompat
import dev.saikrishna.todo.domain.QuickParse
import dev.saikrishna.todo.ui.components.glassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDateTime
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassChrome
import dev.saikrishna.todo.ui.components.glassRim
import dev.saikrishna.todo.ui.components.glassShadow
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.Durations
import dev.saikrishna.todo.ui.motion.Easings
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.theme.Spacing
import kotlinx.coroutines.launch

private val ButtonSize = 44.dp

private enum class TrailingAction { Mic, Send, Stop }

/**
 * Add-task composer (spec 6.3) with natural-language capture and voice (v0.6).
 *
 * - Every edit is parsed by [QuickParse] (pure, on the main thread, microseconds). Recognised
 *   spans get a soft accent highlight drawn behind the text (draw phase, from the text layout),
 *   and a row of small glass chips rises above the capsule. Tapping a chip ignores that
 *   interpretation (the words stay in the title). Submit hands the parse to [onSubmit].
 * - Mic: RECORD_AUDIO is asked on the first tap; while listening the transcript streams into the
 *   field (so chips update live), a restrained level ring breathes behind the stop button (draw
 *   phase; a static ring under reduced motion), and the user confirms with send.
 * Pinned to the bottom, rides the IME via imePadding() only.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun Composer(
    state: TextFieldState,
    onSubmit: (QuickParse.Result) -> Unit,
    modifier: Modifier = Modifier,
    clock: () -> LocalDateTime = LocalDateTime::now,
    /** v0.7: known lists, so "#work" assigns a list (unknown "#word" becomes a tag). */
    lists: List<dev.saikrishna.todo.data.TaskList> = emptyList(),
    /** v0.7: the "+" button opens the add-options menu (templates); null = it just focuses the field. */
    onOptions: (() -> Unit)? = null,
    optionsAnchor: Modifier = Modifier,
    /** v0.7.3: collapse into the centred pill while scrolling down. Null = never collapses. */
    chrome: ComposerChrome? = null,
    /**
     * v0.9: a 48 dp control left of the capsule (the lists button), [ListsButtonGap] away. Expanded
     * it sits at the left margin and the capsule takes the rest; collapsed, button + gap + pill
     * form one pair centred on the screen. Both move on the same progress (same spring), so the
     * pair's centre never moves.
     */
    leading: (@Composable () -> Unit)? = null,
) {
    val c = AppTheme.colors
    val type = AppTheme.type
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val textAlpha = remember { Animatable(1f) }
    var submitting by remember { mutableStateOf(false) }
    val hasText by remember { derivedStateOf { state.text.isNotBlank() } }
    var ignored by remember { mutableStateOf(emptySet<String>()) }
    val currentClock by rememberUpdatedState(clock)
    val currentLists by rememberUpdatedState(lists)
    val parsed by remember { derivedStateOf { QuickParse.parse(state.text.toString(), currentClock(), ignored, currentLists) } }
    val layout = remember { LayoutHolder() }

    // Voice.
    val voice = remember { VoiceInput(context.applicationContext) }
    DisposableEffect(voice) { onDispose { voice.cancel() } }
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint) { if (hint != null) { delay(3_000); hint = null } }
    var voicePrefix by remember { mutableStateOf("") }
    fun startVoice() {
        voicePrefix = state.text.toString().trimEnd().let { if (it.isEmpty()) "" else "$it " }
        voice.start(
            onText = { heard -> state.setTextAndPlaceCursorAtEnd(voicePrefix + heard) },
            onDone = { error -> if (error != null) hint = error },
        )
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else hint = "Microphone permission is off"
    }
    fun onMic() {
        haptics.tick()
        when {
            !voice.available -> hint = "Voice input isn't available on this phone"
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> startVoice()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val level = remember { Animatable(0f) }
    LaunchedEffect(voice, reduced) {
        snapshotFlow { voice.level }.collectLatest { target ->
            if (reduced) level.snapTo(0f) else level.animateTo(target, Springs.Quick.float)
        }
    }

    // Forget ignored interpretations once the field is cleared.
    LaunchedEffect(Unit) {
        snapshotFlow { state.text.isEmpty() }.collect { empty -> if (empty) ignored = emptySet() }
    }

    fun submit() {
        val text = state.text.toString().trim()
        if (text.isEmpty() || submitting) return
        if (voice.listening) voice.stop()
        submitting = true
        haptics.tick() // one haptic per intent, when the add commits
        onSubmit(QuickParse.parse(text, clock(), ignored, lists))
        scope.launch {
            try {
                textAlpha.animateTo(0f, tween(Durations.FadeOutFast, easing = Easings.Standard))
                state.clearText()
                ignored = emptySet()
            } finally {
                textAlpha.snapTo(1f)
                submitting = false
            }
        }
    }

    // v0.7.3 collapse: report what blocks it, run the one-surface morph.
    var focused by remember { mutableStateOf(false) }
    val busy = focused || hasText || voice.listening || parsed.tokens.isNotEmpty()
    if (chrome != null) {
        androidx.compose.runtime.SideEffect { chrome.composerBusy = busy }
        LaunchedEffect(chrome, reduced) {
            snapshotFlow { chrome.collapsed }.collectLatest { target ->
                val to = if (target) 1f else 0f
                if (reduced) {
                    // Crossfade-through (150 ms total), no bounds animation.
                    if (chrome.progress.value != to) {
                        chrome.fade.animateTo(0f, tween(75, easing = Easings.Standard))
                        chrome.progress.snapTo(to)
                        chrome.fade.animateTo(1f, tween(75, easing = Easings.Standard))
                    }
                } else {
                    chrome.fade.snapTo(1f)
                    chrome.progress.animateTo(to, Springs.Morph.float)
                }
            }
        }
    }
    val collapsedTarget = chrome?.collapsed == true
    // v0.7.4: focus must be able to leave the field (tap outside, IME hidden, scroll, navigation).
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    if (chrome != null) {
        androidx.compose.runtime.SideEffect {
            chrome.focused = focused
            chrome.dismiss = {
                if (focused) {
                    chrome.dismissCount++
                    keyboard?.hide()
                    focusManager.clearFocus(force = true)
                }
            }
        }
    }
    // The IME going away (system back, keyboard's hide key) ends editing: drop focus too.
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    val imeWasVisible = remember { booleanArrayOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeWasVisible[0] && !imeVisible && focused) focusManager.clearFocus(force = true)
        imeWasVisible[0] = imeVisible
    }
    fun expandAndFocus() {
        haptics.tick() // the only haptic of the collapse feature: expand by tap
        chrome?.expand()
        focus.requestFocus()
    }
    fun pillMic() {
        chrome?.expand()
        onMic()
    }
    fun k(): Float = chrome?.progress?.value ?: 0f
    /** Placeholder alpha: out over the first 35 % of a collapse, in after 40 % of an expand. */
    fun fieldAlpha(): Float {
        val p = k()
        return if (chrome?.collapsed == true) 1f - dev.saikrishna.todo.ui.components.smoothstep(p / 0.35f)
        else dev.saikrishna.todo.ui.components.smoothstep((0.6f - p) / 0.6f)
    }

    // "+" (and mic) enter/exit: scale 0.8 on Quick + fades; width change on Smooth so the field glides.
    val sideEnter: EnterTransition =
        if (reduced) fadeIn(Tweens.reduced())
        else fadeIn(Tweens.fadeIn(0)) + scaleIn(Springs.Quick.float, initialScale = 0.8f) +
            expandHorizontally(Springs.Smooth.intSize, expandFrom = Alignment.End)
    val sideExit: ExitTransition =
        if (reduced) fadeOut(Tweens.fadeOutFast())
        else fadeOut(Tweens.fadeOutFast()) + scaleOut(Springs.Quick.float, targetScale = 0.8f) +
            shrinkHorizontally(Springs.Smooth.intSize, shrinkTowards = Alignment.End)

    val highlight = c.accent.copy(alpha = if (c.isDark) 0.24f else 0.16f)
    val trailing = when {
        voice.listening -> TrailingAction.Stop
        hasText -> TrailingAction.Send
        else -> TrailingAction.Mic
    }

    // v0.7.4: the composer's touch shield. Child 0 (under the column, so the field and buttons still
    // get their events first) consumes every pointer event over the bar: the capsule, its rim, the
    // gaps between the icons and the divider, 6 dp of shadow above it and everything below it (bottom
    // padding, navigation bar). Width follows the morph in the layout phase: the full width when
    // expanded, the pill + 8 dp each side when collapsed, so taps beside the pill reach the rows.
    val chipsH = remember { IntHolder() }
    val leadW = if (leading != null) ListsButtonSize + ListsButtonGap else 0.dp
    // Collapsed: the pair (button + gap + pill) + 8 dp each side.
    val pillWShield = with(androidx.compose.ui.platform.LocalDensity.current) { (PillWidth + leadW + 16.dp).roundToPx() }
    val full = remember { IntHolder() }
    Layout(
        content = {
            Box(
                Modifier
                    .testTag("composer-shield")
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            while (true) {
                                val e = awaitPointerEvent()
                                e.changes.forEach { it.consume() }
                                if (e.changes.none { it.pressed }) break
                            }
                        }
                    },
            )
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                // Narrower than the cards and floating (reference proportions: ~48 dp capsule, wide side margins).
                .padding(top = 6.dp, bottom = 10.dp),
        ) {
            Box(Modifier.layout { m, cons -> val pl = m.measure(cons); chipsH.v = pl.height; layout(pl.width, pl.height) { pl.place(0, 0) } }) {
                ParseChips(
                    tokens = parsed.tokens,
                    onRemove = { t -> haptics.tick(); ignored = ignored + t.key },
                    lists = lists,
                )
            }
            val pillW = with(androidx.compose.ui.platform.LocalDensity.current) { PillWidth.roundToPx() }
            Box(Modifier.fillMaxWidth()) {
            Layout(
                content = {
                    // 0: the field (fixed width, never reflows during the morph; fades, clipped by the capsule).
                    Box(
                        Modifier
                            .animateContentSize(Springs.Smooth.intSize)
                            .graphicsLayer { alpha = fieldAlpha() }
                            .then(if (collapsedTarget) Modifier.clearAndSetSemantics { } else Modifier),
                    ) {
                    BasicTextField(
                        state = state,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .focusRequester(focus)
                            .graphicsLayer { alpha = textAlpha.value }
                            // Recognised spans: soft accent capsules behind the text, from the live layout.
                            .drawBehind {
                                val tl = layout.get?.invoke() ?: return@drawBehind
                                val len = tl.layoutInput.text.length
                                val padX = 2.dp.toPx()
                                val padY = 1.dp.toPx()
                                val r = CornerRadius(6.dp.toPx())
                                val text = tl.layoutInput.text
                                for (t in parsed.tokens) {
                                    if (t.end > len || t.start >= t.end) continue
                                    // One soft capsule per line the token spans (trailing spaces trimmed).
                                    val first = tl.getLineForOffset(t.start)
                                    val last = tl.getLineForOffset(t.end - 1)
                                    for (line in first..last) {
                                        var s0 = maxOf(t.start, tl.getLineStart(line))
                                        var e0 = minOf(t.end, tl.getLineEnd(line, visibleEnd = true))
                                        while (e0 > s0 && text[e0 - 1].isWhitespace()) e0--
                                        while (s0 < e0 && text[s0].isWhitespace()) s0++
                                        if (s0 >= e0) continue
                                        val left = tl.getHorizontalPosition(s0, usePrimaryDirection = true)
                                        val right = tl.getHorizontalPosition(e0, usePrimaryDirection = true)
                                        val top = tl.getLineTop(line)
                                        val bottom = tl.getLineBottom(line)
                                        drawRoundRect(
                                            highlight,
                                            topLeft = Offset(minOf(left, right) - padX, top - padY),
                                            size = Size(kotlin.math.abs(right - left) + 2 * padX, bottom - top + 2 * padY),
                                            cornerRadius = r,
                                        )
                                    }
                                }
                            },
                        textStyle = type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = 5),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send,
                        ),
                        onKeyboardAction = { submit() },
                        onTextLayout = { getResult -> layout.get = getResult },
                        decorator = { inner ->
                            Box {
                                if (state.text.isEmpty()) {
                                    Text(
                                        hint ?: if (voice.listening) "Listening…" else "Add a task",
                                        style = type.body,
                                        color = if (hint != null) c.warning else c.inkSecondary,
                                        maxLines = 1,
                                    )
                                }
                                inner()
                            }
                        },
                    )

                    }
                    // 1: "+" (hidden while there is text). Always a slot, even when gone: the Layout indexes its children.
                    Box {
                        androidx.compose.animation.AnimatedVisibility(visible = !hasText && !voice.listening, enter = sideEnter, exit = sideExit) {
                            Box(optionsAnchor) {
                                PlusButton(
                                    collapsed = collapsedTarget,
                                    onClick = {
                                        if (collapsedTarget) expandAndFocus()
                                        else { val open = onOptions; if (open != null) { haptics.tick(); open() } else focus.requestFocus() }
                                    },
                                    // Long-press keeps the templates menu reachable from the pill.
                                    onLongClick = onOptions?.let { open -> { haptics.tick(); chrome?.expand(); open() } },
                                )
                            }
                        }
                    }
                    // 2: mic / send / stop.
                    AnimatedContent(
                        targetState = trailing,
                        transitionSpec = {
                            if (reduced) {
                                fadeIn(Tweens.reduced()) togetherWith fadeOut(Tweens.fadeOutFast())
                            } else {
                                (fadeIn(tween(150, easing = Easings.Standard)) +
                                    scaleIn(Springs.Quick.float, initialScale = 0.4f)) togetherWith
                                    (fadeOut(Tweens.fadeOutFast()) + scaleOut(Springs.Quick.float, targetScale = 0.4f))
                            } using SizeTransform(clip = false)
                        },
                        contentAlignment = Alignment.Center,
                        label = "send morph",
                    ) { action ->
                        when (action) {
                            TrailingAction.Send -> SendButton(onClick = ::submit)
                            TrailingAction.Mic -> CircleIconButton(
                                AppIcons.Mic, if (collapsedTarget) "Voice input" else "Dictate",
                                onClick = { if (collapsedTarget) pillMic() else onMic() },
                            )
                            TrailingAction.Stop -> StopButton(level = { level.value }, onClick = { haptics.tick(); voice.stop() })
                        }
                    }
                    // 3: the pill's hairline divider (alpha = progress, draw phase).
                    Box(Modifier.size(1.dp, 20.dp).drawBehind { drawRect(c.inkFill.copy(alpha = c.inkFill.alpha * k())) })
                },
                modifier = Modifier
                    .padding(start = 24.dp + leadW, end = 24.dp)
                    .fillMaxWidth()
                    // v0.7.3: ONE surface whose bounds go from the full composer to the centred pill,
                    // symmetric about the centre (layout phase reads the progress; no recomposition).
                    .layout { m, cons ->
                        val f = cons.maxWidth
                        full.v = f
                        val w = (f + (pillW - f) * k()).roundToInt().coerceIn(minOf(pillW, f), f)
                        val p = m.measure(cons.copy(minWidth = w, maxWidth = w))
                        layout(f, p.height) { p.placeRelative((f - w) / 2, 0) }
                    }
                    .graphicsLayer { alpha = chrome?.fade?.value ?: 1f }
                    .testTag("composer-surface")
                    .then(
                        if (collapsedTarget) Modifier
                            .clearAndSetSemantics {
                                contentDescription = "Add a task"
                                onClick(label = "Add a task") { expandAndFocus(); true }
                                customActions = listOf(CustomAccessibilityAction("Voice input") { pillMic(); true })
                            }
                            // Tap on the pill body (outside its buttons) expands and focuses.
                            .pointerInput(Unit) { detectTapGestures { expandAndFocus() } }
                        else Modifier,
                    )
                    // Restrained liquid glass (v0.5) + v0.7.2 frost: soft diffuse shadow outside the
                    // capsule, live frosted blur under the tint, 1 dp gradient rim.
                    .glassShadow(ComposerShape, GlassLevel.Mid, c.glassShadow)
                    .clip(ComposerShape)
                    .glassChrome()
                    .glassRim(ComposerShape, c.glassRimTop, c.glassRimBottom),
            ) { ms, cons ->
                val f = full.v.takeIf { it > 0 } ?: cons.maxWidth
                val w = cons.maxWidth
                val left = (f - w) / 2 // the capsule's left edge in full-composer coordinates
                val inset = 2.dp.roundToPx()
                val loose = Constraints()
                val plus = ms[1].measure(loose)
                val trail = ms[2].measure(loose)
                val divider = ms[3].measure(loose)
                val fieldW = (f - 12.dp.roundToPx() - inset - plus.width - trail.width).coerceAtLeast(0)
                val field = ms[0].measure(Constraints.fixedWidth(fieldW))
                val h = maxOf(field.height, ButtonSize.roundToPx()) + 2 * inset
                val p = k()
                val gap = PillGap.roundToPx()
                fun lerpI(a: Int, b: Int) = (a + (b - a) * p).roundToInt()
                val plusX = lerpI(f - inset - trail.width - plus.width, f / 2 - gap / 2 - ButtonSize.roundToPx())
                val trailX = lerpI(f - inset - trail.width, f / 2 + gap / 2)
                layout(w, h) {
                    field.place(12.dp.roundToPx() - left, h - inset - field.height)
                    plus.place(plusX - left, h - inset - plus.height)
                    trail.place(trailX - left, h - inset - trail.height)
                    // Midway between the two buttons: F/2 once collapsed, never ahead of them mid-morph.
                    val mid = (plusX + ButtonSize.roundToPx() + trailX) / 2
                    divider.place(mid - divider.width / 2 - left, (h - divider.height) / 2)
                }
            }
            if (leading != null) {
                // v0.9: the lists button, bottom-aligned with the capsule. x = 24 dp + k·(F − pill)/2,
                // where F is the capsule's full width: collapsed, the button sits ListsButtonGap left
                // of the centred pill and the pair is centred on the screen (layout phase only).
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .layout { m, cons ->
                            val pl = m.measure(Constraints())
                            val x = 24.dp.roundToPx() + ((full.v - pillW).coerceAtLeast(0) / 2f * k()).roundToInt()
                            layout(pl.width, pl.height) { pl.place(x, 0) }
                        }
                        .graphicsLayer { alpha = chrome?.fade?.value ?: 1f },
                ) { leading() }
            }
            }
        }
        },
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { chrome?.composerBounds = it.boundsInRoot() },
    ) { ms, cons ->
        val column = ms[1].measure(cons)
        val f = column.width
        val w = (f + (pillWShield - f) * k()).roundToInt().coerceIn(minOf(pillWShield, f), f)
        val top = chipsH.v.coerceAtMost(column.height)
        val shield = ms[0].measure(Constraints.fixed(w, column.height - top))
        layout(f, column.height) {
            shield.place((f - w) / 2, top)
            column.place(0, 0)
        }
    }
}

/** v0.7.3 pill: 120 dp wide (4 dp · 44 dp "+" · 24 dp gap with a hairline · 44 dp mic · 4 dp). */
val PillWidth = 120.dp
private val PillGap = 24.dp

private class IntHolder { var v = 0 }

/** "+": expanded = add options (templates); collapsed pill = add a task (long-press = templates). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlusButton(collapsed: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val c = AppTheme.colors
    val label = if (collapsed) "Add a task" else "Add options"
    Box(
        Modifier
            .size(ButtonSize)
            .clip(Shapes.full)
            .combinedClickable(
                role = Role.Button, onClickLabel = label, onClick = onClick,
                onLongClickLabel = if (onLongClick != null) "Templates" else null, onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Plus, contentDescription = label, tint = c.ink, modifier = Modifier.size(22.dp))
    }
}

@Stable
private class LayoutHolder { var get by mutableStateOf<(() -> TextLayoutResult?)?>(null) }

/** Small glass chips for what the parser recognised; tap removes that interpretation. */
@Composable
internal fun ParseChips(
    tokens: List<QuickParse.Token>,
    onRemove: (QuickParse.Token) -> Unit,
    lists: List<dev.saikrishna.todo.data.TaskList> = emptyList(),
    /** v0.8: the capture card insets its chips less than the full-width composer. */
    inset: androidx.compose.ui.unit.Dp = 28.dp,
) {
    val c = AppTheme.colors
    fun listColor(t: QuickParse.Token): androidx.compose.ui.graphics.Color? =
        if (t.kind != QuickParse.Kind.List) null
        else lists.firstOrNull { "List:" + it.handle == t.key }?.let { c.listColor(it.color) }
    val reduced = LocalReducedMotion.current
    // Keep showing the last non-empty set while the row collapses.
    var shown by remember { mutableStateOf(tokens) }
    if (tokens.isNotEmpty()) shown = tokens
    AnimatedVisibility(
        visible = tokens.isNotEmpty(),
        enter = if (reduced) fadeIn(Tweens.reduced()) else fadeIn(Tweens.fadeIn(0)) + expandVertically(Springs.Smooth.intSize, expandFrom = Alignment.Bottom),
        exit = if (reduced) fadeOut(Tweens.fadeOutFast()) else fadeOut(Tweens.fadeOutFast()) + shrinkVertically(Springs.Smooth.intSize, shrinkTowards = Alignment.Bottom),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                // Full-bleed scroller; the chips start in line with the capsule.
                .padding(start = inset, end = inset, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (t in shown) key(t.key) { ParseChip(t, onRemove, listColor(t)) }
        }
    }
}

@Composable
private fun ParseChip(token: QuickParse.Token, onRemove: (QuickParse.Token) -> Unit, dot: androidx.compose.ui.graphics.Color? = null) {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    // Each chip pops in once (scale 0.9 → 1 on Quick, read in the layer); reduced: fade only.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, if (reduced) Tweens.reduced() else Springs.Quick.float) }
    val icon = when (token.kind) {
        QuickParse.Kind.Date -> AppIcons.Calendar
        QuickParse.Kind.Time -> AppIcons.Clock
        QuickParse.Kind.Repeat -> AppIcons.Repeat
        QuickParse.Kind.Priority -> AppIcons.Flag
        QuickParse.Kind.List -> AppIcons.Lists
        QuickParse.Kind.Tag -> AppIcons.Hash
    }
    val tint = if (token.kind == QuickParse.Kind.Priority && token.label == "High") c.danger else c.ink
    Row(
        Modifier
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                if (!reduced) { val s = 0.9f + 0.1f * appear.value; scaleX = s; scaleY = s }
            }
            .height(30.dp)
            .glassSurface(Shapes.full, GlassLevel.Low)
            .clip(Shapes.full)
            .clickable(role = Role.Button, onClickLabel = "Remove ${token.label}") { onRemove(token) }
            .semantics { contentDescription = "${token.label}, tap to keep as text" }
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) ListDot(dot, 8.dp) else Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        // Tags already carry their "#": show the word only next to the hash glyph.
        Text(if (token.kind == QuickParse.Kind.Tag) token.label.removePrefix("#") else token.label, style = AppTheme.type.meta, color = tint, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icon(AppIcons.Close, null, tint = c.inkTertiary, modifier = Modifier.size(12.dp))
    }
}

/** Ink stop button with a restrained live level ring (draw phase). */
@Composable
private fun StopButton(level: () -> Float, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .size(ButtonSize)
            .drawBehind {
                val l = level()
                val base = 20.dp.toPx()
                drawCircle(c.accent.copy(alpha = 0.10f + 0.12f * l), radius = base + 6.dp.toPx() * l)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(Shapes.full)
                .background(c.ink)
                .clickable(role = Role.Button, onClickLabel = "Stop listening", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Stop, contentDescription = "Stop listening", tint = c.onInk, modifier = Modifier.size(18.dp))
        }
    }
}

/** Capsule while single-line (44 dp content + 2 dp inset = 48 dp), soft 24 dp corners when it grows. */
private val ComposerShape = RoundedCornerShape(24.dp)

@Composable
private fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .size(ButtonSize)
            .clip(Shapes.full)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = c.ink, modifier = Modifier.size(22.dp))
    }
}

/** 40 dp ink circle with a paper up-arrow, centred in a 44 dp slot. */
@Composable
private fun SendButton(onClick: () -> Unit) {
    val c = AppTheme.colors
    Box(Modifier.size(ButtonSize), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(40.dp)
                .clip(Shapes.full)
                .background(c.ink)
                .clickable(role = Role.Button, onClickLabel = "Add task", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.ArrowUp, contentDescription = "Add task", tint = c.onInk, modifier = Modifier.size(20.dp))
        }
    }
}
