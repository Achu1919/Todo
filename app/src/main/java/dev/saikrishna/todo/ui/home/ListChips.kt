package dev.saikrishna.todo.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.data.TaskList
import dev.saikrishna.todo.ui.components.animateStateColor
import dev.saikrishna.todo.ui.components.glassControl
import dev.saikrishna.todo.ui.components.pressScale
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.PillSpec
import dev.saikrishna.todo.ui.theme.Shapes

/** v0.12 list chip: [id] null = "All"; [color] = list colour index (null for All). */
@Immutable
data class ChipOption(val id: Long?, val label: String, val color: Int?, val count: Int)

/**
 * Pure: the chips for one page: All (every row) + each list in list order that has rows on the
 * page, or is the current choice. [lists] are the lists in view (focus/selection already applied).
 */
fun chipOptions(items: List<ListItem>, lists: List<TaskList>, selected: Long?): List<ChipOption> {
    val counts = countsByList(items)
    val all = ChipOption(null, "All", null, counts.values.sum())
    return listOf(all) + lists.sortedWith(compareBy<TaskList> { it.position }.thenBy { it.id })
        .filter { (counts[it.id] ?: 0) > 0 || it.id == selected }
        .map { l -> ChipOption(l.id, listOfNotNull(l.emoji, l.name).joinToString(" "), l.color, counts[l.id] ?: 0) }
}

/**
 * Chip row under the page heading (Today / Upcoming / Done). Selected "All" = ink capsule;
 * selected list = a wash + rim in the list colour. Fill, rim and label run on `select` through
 * [animateStateColor] (only the on/off progress animates, the colours are read fresh, so a theme
 * reveal never tweens them).
 */
@Composable
fun ListChipRow(options: List<ChipOption>, selected: Long?, onSelect: (Long?) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (o in options) ListChip(o, o.id == selected) { if (o.id != selected) onSelect(o.id) }
    }
}

@Composable
private fun ListChip(option: ChipOption, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val haptics = LocalHaptics.current
    val list = option.color?.let { c.listColor(it) }
    val onFill = if (list == null) c.ink else list.copy(alpha = 0.18f)
    val fill by animateStateColor(selected, onFill, onFill.copy(alpha = 0f), Tweens.select(), label = "chip fill")
    val rimOn = list?.copy(alpha = 0.55f) ?: Color.Transparent
    val rim by animateStateColor(selected, rimOn, rimOn.copy(alpha = 0f), Tweens.select(), label = "chip rim")
    val text by animateStateColor(selected && list == null, c.onInk, c.ink, Tweens.select(), label = "chip text")
    val count by animateStateColor(selected && list == null, c.onInk.copy(alpha = 0.7f), c.inkTertiary, Tweens.select(), label = "chip count")
    // v0.12.1: the glass rim is drawn over content, so it fades out as the chip fills: a selected
    // chip is a clean solid fill (no light line inside its top edge), light and dark.
    val rimOff by androidx.compose.animation.core.animateFloatAsState(if (selected) 0f else 1f, Tweens.select(), label = "chip glass rim")
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .height(PillSpec.ChipHeight)
            .pressScale(press, 0.95f)
            .glassControl(Shapes.full, rimAlpha = { rimOff })
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(fill, cornerRadius = r)
                val w = 1.dp.toPx()
                drawRoundRect(rim, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), cornerRadius = r, style = Stroke(w))
            }
            .clip(Shapes.full)
            .clickable(
                interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current,
                role = Role.RadioButton, onClick = { haptics.tick(); onClick() },
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (list != null) { ListDot(list, 8.dp); Spacer(Modifier.width(6.dp)) }
        BasicText(option.label, style = AppTheme.type.label, color = { text }, maxLines = 1)
        Spacer(Modifier.width(5.dp))
        BasicText("${option.count}", style = AppTheme.type.meta, color = { count }, maxLines = 1)
    }
}

