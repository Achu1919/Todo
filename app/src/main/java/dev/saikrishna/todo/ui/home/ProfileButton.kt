package dev.saikrishna.todo.ui.home

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.ui.components.glassControl
import dev.saikrishna.todo.ui.components.pressScale
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.PillSpec
import dev.saikrishna.todo.ui.theme.Shapes
import java.text.BreakIterator
import java.util.Locale

/**
 * v0.13.3: the first user-perceived character (grapheme cluster) of [name], for the profile circle.
 * Emoji (incl. ZWJ sequences, flags, skin tones) and combining marks stay whole. Uppercased only when
 * that keeps it one grapheme (so "ß" stays "ß", not "SS"; scripts without case are unchanged).
 * Null for a blank name.
 */
fun profileInitial(name: String, locale: Locale = Locale.getDefault()): String? {
    val t = name.trim()
    if (t.isEmpty()) return null
    val it = BreakIterator.getCharacterInstance(locale)
    it.setText(t)
    val end = it.next().takeIf { e -> e != BreakIterator.DONE } ?: t.length
    val g = t.substring(0, end)
    val up = g.uppercase(locale)
    if (up == g) return g
    val check = BreakIterator.getCharacterInstance(locale).apply { setText(up) }
    return if (check.next() == up.length) up else g
}

/**
 * v0.13.3: the Settings entry on Home — a personal circle (the Name's first grapheme, or a person
 * glyph when no name is set). Deliberately unlike the top bar's glass controls: a filled disc tinted
 * with the accent and a soft accent ring. Same 44 dp target, soft-press tint and 0.94 press scale
 * (which already snaps under reduced motion). Kept on the "home-settings" tag.
 */
@Composable
fun ProfileButton(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val press = remember { MutableInteractionSource() }
    val initial = remember(name) { profileInitial(name) }
    val markSize = with(LocalDensity.current) { 21.dp.toSp() }
    Box(
        modifier
            .size(PillSpec.Control)
            .pressScale(press, 0.94f)
            .glassControl(Shapes.full)
            .clip(Shapes.full)
            .background(c.accent.copy(alpha = if (c.isDark) 0.26f else 0.17f), Shapes.full)
            .border(1.dp, c.accent.copy(alpha = if (c.isDark) 0.45f else 0.32f), Shapes.full)
            .clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button, onClickLabel = "Open settings", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Settings, profile" }
            .testTag("home-settings"),
        contentAlignment = Alignment.Center,
    ) {
        if (initial == null) {
            Icon(AppIcons.Person, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp).testTag("profile-glyph").semantics { hideFromAccessibility() })
        } else {
            // Fixed size (not scaled with the font): it is a mark inside a fixed 44 dp circle.
            Text(
                initial,
                style = AppTheme.type.display.copy(fontSize = markSize, lineHeight = markSize, fontWeight = FontWeight.Medium),
                color = c.ink,
                maxLines = 1,
                modifier = Modifier.testTag("profile-initial").semantics { hideFromAccessibility() },
            )
        }
    }
}
