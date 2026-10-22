package dev.saikrishna.todo.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.ThemeMode
import dev.saikrishna.todo.ui.components.glassControl
import dev.saikrishna.todo.ui.components.pressScale
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.SpringToken
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.PillSpec
import dev.saikrishna.todo.ui.theme.Shapes
import kotlin.math.cos
import kotlin.math.sin

/** v0.9.1 icon morph spring: ~0.35 s, near-critical. */
private val IconMorph = SpringToken(0.35f, 0.90f)

/**
 * Our own sun ↔ moon, drawn on a 24-unit grid with the AppIcons stroke (2 units, round caps).
 * [moon] 0 = sun (disc r 4 + 8 rays), 1 = crescent: the rays retract while rotating 45°, the disc
 * grows to r 8 and a bite circle slides in from the top right; the outline is disc − bite.
 */
@Composable
fun SunMoonIcon(moon: () -> Float, tint: Color, modifier: Modifier = Modifier) {
    val disc = remember { Path() }
    val bite = remember { Path() }
    val out = remember { Path() }
    Canvas(modifier.size(22.dp)) {
        val u = size.minDimension / 24f
        val p = moon().coerceIn(0f, 1.2f)
        val pc = p.coerceIn(0f, 1f)
        val cx = 12f * u; val cy = 12f * u
        val stroke = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val r = (4.5f + 3.5f * p) * u
        disc.reset(); disc.addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), r))
        // Bite: from fully outside (no effect) to 0.62 r from the centre, up and to the right.
        val br = r * 0.82f
        val dist = (r + br + 2f * u) + (r * 0.62f - (r + br + 2f * u)) * pc
        val bx = cx + dist * 0.7071f; val by = cy - dist * 0.7071f
        bite.reset(); bite.addOval(androidx.compose.ui.geometry.Rect(Offset(bx, by), br))
        out.reset(); out.op(disc, bite, PathOperation.Difference)
        drawPath(out, tint, style = stroke)
        val ray = 1f - pc
        if (ray > 0.02f) {
            val rot = Math.toRadians(45.0 * pc)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0) + rot
                val c = cos(a).toFloat(); val s = sin(a).toFloat()
                val r0 = (7f + 1.5f * pc) * u
                val r1 = r0 + 2.4f * u * ray
                drawLine(tint.copy(alpha = tint.alpha * ray), Offset(cx + r0 * c, cy + r0 * s), Offset(cx + r1 * c, cy + r1 * s), 2f * u, StrokeCap.Round)
            }
        }
    }
}

/** The icon state for [dark]: a moon on the light theme (tap → dark), a sun on the dark theme. */
@Composable
fun rememberSunMoon(dark: Boolean): Animatable<Float, *> {
    val moon = remember { Animatable(if (dark) 0f else 1f) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(dark) {
        val target = if (dark) 0f else 1f
        if (reduced) moon.snapTo(target) else moon.animateTo(target, IconMorph.float)
    }
    return moon
}

/**
 * v0.9.1 header theme toggle: the mirror of the search button (44 dp glass circle). Tap toggles
 * light/dark; long-press opens the System / Light / Dark dropdown. [anchor] registers it for the
 * dropdown morph.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeToggleButton(
    dark: Boolean,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val moon = rememberSunMoon(dark)
    val press = remember { MutableInteractionSource() }
    val label = if (dark) "Switch to light theme" else "Switch to dark theme"
    Box(
        modifier
            .then(anchor)
            .size(PillSpec.Control)
            .testTag("theme-toggle")
            .pressScale(press, 0.94f)
            .glassControl(Shapes.full)
            .clip(Shapes.full)
            .combinedClickable(
                interactionSource = press, indication = LocalIndication.current, role = Role.Button,
                onClickLabel = label, onLongClickLabel = "Choose theme",
                onClick = onToggle, onLongClick = onMenu,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { SunMoonIcon({ moon.value }, c.ink) }
}

/** The button's content without its surface: the dropdown morph's ghost. */
@Composable
fun ThemeToggleContent(dark: Boolean) {
    Box(Modifier.size(PillSpec.Control), contentAlignment = Alignment.Center) {
        SunMoonIcon({ if (dark) 0f else 1f }, AppTheme.colors.ink)
    }
}

/** v0.9.1 long-press dropdown: System / Light / Dark with a check on the current mode. */
@Composable
fun ThemeMenuContent(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    Column(Modifier.padding(vertical = 6.dp).testTag("theme-menu")) {
        for (m in ThemeMode.entries) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.RadioButton) { haptics.tick(); onSelect(m) }
                    .semantics { selected = m == current }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    when (m) {
                        ThemeMode.System -> Canvas(Modifier.size(18.dp)) {
                            // Half-filled circle: "follow the system".
                            val sw = 1.8.dp.toPx(); val rr = size.minDimension / 2f - sw / 2f
                            drawCircle(c.inkSecondary, rr, style = Stroke(sw))
                            drawArc(c.inkSecondary, 90f, 180f, useCenter = true, topLeft = Offset(center.x - rr, center.y - rr), size = androidx.compose.ui.geometry.Size(2 * rr, 2 * rr))
                        }
                        ThemeMode.Light -> SunMoonIcon({ 0f }, c.inkSecondary, Modifier.size(20.dp))
                        ThemeMode.Dark -> SunMoonIcon({ 1f }, c.inkSecondary, Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(m.name, style = AppTheme.type.body, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (m == current) Icon(AppIcons.Check, "Selected", tint = c.ink, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
