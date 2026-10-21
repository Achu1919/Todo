package dev.saikrishna.todo.ui.settings

import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassSurface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.motion.Tweens
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes

private class Notice(val name: String, val licence: String, val asset: String? = null)

private val Notices = listOf(
    Notice("Inter", "SIL Open Font License 1.1", "licenses/OFL-Inter.txt"),
    Notice("Newsreader", "SIL Open Font License 1.1", "licenses/OFL-Newsreader.txt"),
    Notice("JetBrains Mono", "SIL Open Font License 1.1", "licenses/OFL-JetBrainsMono.txt"),
    Notice("AndroidX & Jetpack Compose", "Apache License 2.0"),
    Notice("Kotlin & kotlinx.coroutines", "Apache License 2.0"),
    Notice("Haze (Chris Banes)", "Apache License 2.0"),
    Notice("Reorderable (Calvin Liang)", "Apache License 2.0"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Licences", onBack = onBack) {
        Spacer(Modifier.height(12.dp))
        Notices.forEach { NoticeCard(it) }
        Text(
            "Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0",
            style = AppTheme.type.meta,
            color = AppTheme.colors.inkSecondary,
            modifier = Modifier.padding(4.dp),
        )
    }
}

@Composable
private fun NoticeCard(n: Notice) {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    val context = LocalContext.current
    var open by rememberSaveable(n.name) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 90f else 0f, if (reduced) Tweens.reduced() else Springs.Snappy.float, label = "chevron")
    Column(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .glassSurface(Shapes.xl, GlassLevel.Low, c.glassCard)
            .clip(Shapes.xl),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (n.asset != null) Modifier.clickable(role = Role.Button) { open = !open } else Modifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(n.name, style = AppTheme.type.title, color = c.ink)
                Text(n.licence, style = AppTheme.type.meta, color = c.inkSecondary)
            }
            if (n.asset != null) {
                Icon(
                    AppIcons.ChevronRight, if (open) "Hide licence" else "Show licence", tint = c.inkTertiary,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
                )
            }
        }
        if (n.asset != null) {
            AnimatedVisibility(
                visible = open,
                enter = if (reduced) fadeIn(Tweens.reduced()) else expandVertically(Springs.Smooth.intSize) + fadeIn(Tweens.fadeIn()),
                exit = if (reduced) fadeOut(Tweens.reduced()) else shrinkVertically(Springs.Smooth.intSize) + fadeOut(Tweens.fadeOutFast()),
            ) {
                val text = remember(n.asset) {
                    runCatching { context.assets.open(n.asset).bufferedReader().use { it.readText() } }.getOrDefault("")
                }
                Text(text, style = AppTheme.type.mono, color = c.inkSecondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
            }
        }
    }
}
