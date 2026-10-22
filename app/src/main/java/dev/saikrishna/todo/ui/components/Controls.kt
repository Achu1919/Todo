package dev.saikrishna.todo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.PillSpec
import dev.saikrishna.todo.ui.theme.Shapes
import kotlin.math.roundToInt

/**
 * 44 dp circular icon button. [glass] = floating control (translucent fill + rim, ink icon) for
 * headers; otherwise a bare icon (inside the composer, on cards).
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
) {
    val c = AppTheme.colors
    // v0.9.1: circular in-bounds tint (SoftPress) + 0.94 scale spring; no square ripple.
    val press = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier
            .size(PillSpec.Control)
            .pressScale(press, 0.94f)
            .then(if (glass) Modifier.glassControl(Shapes.full) else Modifier)
            .clip(Shapes.full)
            .clickable(
                interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current,
                role = Role.Button, onClickLabel = description, onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = description,
            tint = if (glass) c.ink else c.inkSecondary,
            modifier = Modifier.size(if (glass) 20.dp else 22.dp),
        )
    }
}

/** Where a pill sits: floating over content (glass track) or inline on a card/sheet (ink-5 % track). */
enum class PillStyle { Floating, Inline }

/**
 * Segmented pill (spec 6.2, proportions from the reference study, see [PillSpec]).
 *
 * - Equal-width segments: max(76 dp, widest label + 2 × 14 dp), squeezed equally if the row is narrow.
 * - Track: Floating = translucent glass + 1 dp light rim, 48 dp; Inline = ink 5 %, 40 dp.
 * - Indicator: an ink capsule inset 2 dp, sliding on the Quick spring (reduced motion: short tween).
 * - Labels are drawn once, opaque, in an offscreen layer, then recoloured with `SrcIn`: onInk inside
 *   the indicator, ink 50 % outside. So a label turns light exactly where the capsule covers it,
 *   mid-slide too, with no per-frame recomposition (only draw reads the animated x).
 * - One `tick` per change; Role.Tab semantics in a selectable group.
 */
@Composable
fun <T> SegmentedPill(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Inline,
) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val height: Dp = if (style == PillStyle.Floating) PillSpec.Height else 40.dp
    val segW = remember { mutableFloatStateOf(0f) }
    val index = options.indexOf(selected).coerceAtLeast(0)
    // Indicator position in SEGMENT units, initialised to the current segment. The capsule is
    // drawn at x × segW, and segW is measured in the same frame before draw, so the capsule is
    // present in the very first frame this pill is drawn (e.g. when Home is recreated by a Nav3
    // pop). It never waits for an effect to run, and only an index change animates.
    val x = remember { Animatable(index.toFloat()) }
    LaunchedEffect(index) {
        if (x.targetValue == index.toFloat() && !x.isRunning) return@LaunchedEffect
        x.animateTo(index.toFloat(), if (reduced) Tweens.reduced() else Springs.Quick.float)
    }

    val trackModifier = when (style) {
        PillStyle.Floating -> Modifier.glassControl(Shapes.full)
        PillStyle.Inline -> Modifier.background(c.inkFill, Shapes.full)
    }

    Layout(
        content = {
            options.forEachIndexed { i, option ->
                val isSelected = i == index
                Box(
                    Modifier
                        .clip(Shapes.full)
                        .selectable(selected = isSelected, role = Role.Tab) {
                            if (!isSelected) { haptics.tick(); onSelect(option) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        label(option),
                        style = AppTheme.type.label,
                        color = { c.ink }, // opaque; recoloured below
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        },
        modifier = modifier
            .height(height)
            .then(trackModifier)
            .padding(PillSpec.Inset)
            .drawBehind {
                if (segW.floatValue > 0f) drawRoundRect(
                    color = c.ink,
                    topLeft = Offset(x.value * segW.floatValue, 0f),
                    size = Size(segW.floatValue, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val l = x.value * segW.floatValue
                val r = l + segW.floatValue
                clipRect(left = l, right = r) { drawRect(c.onInk, blendMode = BlendMode.SrcIn) }
                clipRect(left = l, right = r, clipOp = ClipOp.Difference) {
                    drawRect(c.inkSecondary, blendMode = BlendMode.SrcIn)
                }
            }
            .selectableGroup(),
    ) { measurables, constraints ->
        val n = measurables.size.coerceAtLeast(1)
        val pad = PillSpec.SegmentPadding.roundToPx()
        val natural = measurables.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
        var w = maxOf(PillSpec.MinSegment.roundToPx(), natural + 2 * pad - 12.dp.roundToPx())
        if (constraints.hasBoundedWidth && w * n > constraints.maxWidth) w = constraints.maxWidth / n
        val h = constraints.maxHeight
        // Measured segment width, read by the draw phase (indicator = x × segW).
        if (segW.floatValue != w.toFloat()) segW.floatValue = w.toFloat()
        val placeables = measurables.map { it.measure(Constraints.fixed(w, h)) }
        layout(w * n, h) { placeables.forEachIndexed { i, p -> p.place(i * w, 0) } }
    }
}

/** Capsule chip: ink when selected (onInk label), ink 6 % otherwise. Colours tween on `select`, read in draw. */
@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val fill by animateColorAsState(if (selected) c.ink else c.inkFill, Tweens.select(), label = "chip fill")
    val text by animateColorAsState(if (selected) c.onInk else c.ink, Tweens.select(), label = "chip text")
    Box(
        modifier
            .height(PillSpec.ChipHeight)
            .clip(Shapes.full)
            .drawBehind { drawRect(fill) }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = AppTheme.type.label, color = { text }, maxLines = 1)
    }
}

/** Our switch: ink track when on, thumb slides on Quick (read in the layout phase). */
@Composable
fun MotionSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val reduced = LocalReducedMotion.current
    val p = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) { p.animateTo(if (checked) 1f else 0f, if (reduced) Tweens.reduced() else Springs.Quick.float) }
    val track by animateColorAsState(if (checked) c.ink else c.inkStroke, Tweens.select(), label = "switch track")
    val thumb by animateColorAsState(if (checked) c.onInk else c.glassSolid.copy(alpha = 1f), Tweens.select(), label = "switch thumb")
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(Shapes.full)
            .drawBehind { drawRect(track) }
            .toggleable(value = checked, role = Role.Switch) { haptics.tick(); onCheckedChange(it) }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset((4.dp.toPx() + p.value * 20.dp.toPx()).roundToInt(), 0) }
                .size(24.dp)
                .clip(Shapes.full)
                .drawBehind { drawRect(thumb) },
        )
    }
}
