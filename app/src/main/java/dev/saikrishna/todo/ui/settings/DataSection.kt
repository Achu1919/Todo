package dev.saikrishna.todo.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.saikrishna.todo.TodoApp
import dev.saikrishna.todo.data.Backup
import dev.saikrishna.todo.data.BackupCodec
import dev.saikrishna.todo.data.BackupException
import dev.saikrishna.todo.data.BackupManager
import dev.saikrishna.todo.data.ImportMode
import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassControl
import dev.saikrishna.todo.ui.components.glassSurface
import dev.saikrishna.todo.ui.components.pressScale
import dev.saikrishna.todo.ui.motion.Durations
import dev.saikrishna.todo.ui.motion.LocalHaptics
import dev.saikrishna.todo.ui.motion.LocalReducedMotion
import dev.saikrishna.todo.ui.motion.Springs
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** v0.10 import flow states (the sheet is a pure function of this). */
@Immutable
sealed interface ImportStep {
    data class Preview(val backup: Backup, val currentTasks: Int) : ImportStep
    data class ConfirmReplace(val backup: Backup, val currentTasks: Int) : ImportStep
    data object Working : ImportStep
    data class Done(val message: String) : ImportStep
    data class Failed(val message: String) : ImportStep
}

/** The Settings → Data card: export, import, share open tasks. Owns the SAF launchers. */
@Composable
internal fun DataCard(onStep: (ImportStep?) -> Unit, status: String?, onStatus: (String?) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? TodoApp
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null || app == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val snapshot = app.backup.snapshot()
                    val text = BackupCodec.encode(snapshot)
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    snapshot.summary
                }
            }
            onStatus(result.fold({ "Saved: ${it.headline}" }, { "Couldn't save the backup." }))
            if (result.isSuccess) haptics.tick()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null || app == null) return@rememberLauncherForActivityResult
        scope.launch {
            val step = withContext(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)!!.use { s ->
                        val bytes = s.readNBytes(BackupCodec.MaxBytes + 1)
                        if (bytes.size > BackupCodec.MaxBytes) throw BackupException("This file is too large to be a backup.")
                        String(bytes, Charsets.UTF_8)
                    }
                    ImportStep.Preview(BackupCodec.decode(text), app.database.backupDao().tasks().size)
                } catch (e: BackupException) {
                    ImportStep.Failed(e.message ?: "This backup can't be read.")
                } catch (e: Exception) {
                    ImportStep.Failed("Couldn't open that file.")
                }
            }
            onStep(step)
        }
    }

    Card {
        LinkRow(
            title = "Export backup",
            subtitle = status ?: "Everything (tasks, lists, templates, views, settings) as one JSON file.",
            onClick = { onStatus(null); exporter.launch(BackupCodec.fileName(LocalDate.now())) },
        )
        Divider()
        LinkRow(
            title = "Import backup",
            subtitle = "Preview first, then merge or replace.",
            onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
        Divider()
        LinkRow(
            title = "Share open tasks",
            subtitle = "A Markdown checklist, to any app.",
            onClick = {
                if (app == null) return@LinkRow
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        val all = app.backup.snapshot()
                        BackupCodec.markdown(all.tasks, all.lists)
                    }
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Open tasks")
                        .putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, "Share open tasks").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
    }
}

/** Runs the chosen import; the sheet shows Working, then Done/Failed. */
internal suspend fun runImport(manager: BackupManager, backup: Backup, mode: ImportMode, onStep: (ImportStep?) -> Unit) {
    onStep(ImportStep.Working)
    val step = try {
        val r = withContext(Dispatchers.IO) { manager.import(backup, mode) }
        ImportStep.Done(
            if (mode == ImportMode.Replace) "Replaced with ${backup.summary.headline}."
            else listOfNotNull(
                "${r.added} added",
                r.updated.takeIf { it > 0 }?.let { "$it updated" },
                r.skipped.takeIf { it > 0 }?.let { "$it already here" },
            ).joinToString(", ").replaceFirstChar { it.uppercase() } + ".",
        )
    } catch (e: Exception) {
        ImportStep.Failed("Import failed. Nothing was changed.")
    }
    onStep(step)
}

/**
 * Import sheet over Settings: preview summary, Merge or Replace (danger), a Replace confirmation,
 * then the result. Glass card on the sheet scrim; spring scale-in, crossfade under reduced motion.
 */
@Composable
fun ImportSheet(
    step: ImportStep?,
    onMerge: (Backup) -> Unit,
    onReplace: (Backup, Int) -> Unit,
    onConfirmReplace: (Backup) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = AppTheme.colors
    val reduced = LocalReducedMotion.current
    var last by remember { mutableStateOf(step) }
    if (step != null) last = step
    if (step != null && step != ImportStep.Working) BackHandler(onBack = onDismiss)
    AnimatedVisibility(
        visible = step != null,
        enter = fadeIn(tween(Durations.FadeIn)),
        exit = fadeOut(tween(Durations.FadeOut)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrimSheet)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (step != ImportStep.Working) onDismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .animateEnterExit(
                        enter = if (reduced) fadeIn(tween(Durations.ReducedCrossfade)) else scaleIn(Springs.Morph.float, initialScale = 0.94f),
                        exit = if (reduced) fadeOut(tween(Durations.ReducedCrossfade)) else scaleOut(tween(Durations.FadeOut), targetScale = 0.97f),
                    )
                    .glassSurface(Shapes.xl, GlassLevel.High, c.glassSolid)
                    .clip(Shapes.xl)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(20.dp)
                    .testTag("import-sheet"),
            ) {
                when (val s = last) {
                    is ImportStep.Preview -> PreviewBody(s.backup, s.currentTasks, onMerge, onReplace, onDismiss)
                    is ImportStep.ConfirmReplace -> ConfirmBody(s.backup, s.currentTasks, onConfirmReplace, onDismiss)
                    ImportStep.Working -> {
                        SheetTitle("Importing…")
                        Text("One moment.", style = AppTheme.type.body, color = c.inkSecondary)
                    }
                    is ImportStep.Done -> {
                        SheetTitle("Imported")
                        Text(s.message, style = AppTheme.type.body, color = c.ink)
                        Spacer(Modifier.height(18.dp))
                        SheetButton("Done", fill = c.ink, content = c.onInk, onClick = onDismiss)
                    }
                    is ImportStep.Failed -> {
                        SheetTitle("Can't import")
                        Text(s.message, style = AppTheme.type.body, color = c.ink)
                        Spacer(Modifier.height(18.dp))
                        SheetButton("OK", fill = null, content = c.ink, onClick = onDismiss)
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text, style = AppTheme.type.display, color = AppTheme.colors.ink,
        modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun PreviewBody(b: Backup, current: Int, onMerge: (Backup) -> Unit, onReplace: (Backup, Int) -> Unit, onCancel: () -> Unit) {
    val c = AppTheme.colors
    val s = b.summary
    SheetTitle("Import backup")
    Text(s.headline, style = AppTheme.type.title, color = c.ink, modifier = Modifier.testTag("import-summary"))
    Spacer(Modifier.height(2.dp))
    Text(s.detail, style = AppTheme.type.meta, color = c.inkSecondary)
    Text(
        "Exported ${BackupManager.exportedLabel(s.exportedAt)} · app ${b.appVersion}",
        style = AppTheme.type.meta, color = c.inkSecondary,
    )
    Spacer(Modifier.height(18.dp))
    SheetButton("Merge", fill = c.ink, content = c.onInk, onClick = { onMerge(b) })
    Text(
        "Adds what's new and updates matching items. Nothing is deleted.",
        style = AppTheme.type.meta, color = c.inkSecondary,
        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp, start = 4.dp, end = 4.dp),
    )
    SheetButton("Replace everything", fill = null, content = c.danger, onClick = { onReplace(b, current) })
    Text(
        "Deletes your current tasks, lists, templates and views first.",
        style = AppTheme.type.meta, color = c.inkSecondary,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
    )
    SheetButton("Cancel", fill = null, content = c.inkSecondary, onClick = onCancel, quiet = true)
}

@Composable
private fun ConfirmBody(b: Backup, current: Int, onConfirm: (Backup) -> Unit, onCancel: () -> Unit) {
    val c = AppTheme.colors
    SheetTitle("Replace everything?")
    Text(
        "Your ${dev.saikrishna.todo.data.BackupSummary.plural(current, "current task")} and all lists, templates and saved views " +
            "will be deleted, then replaced by ${b.summary.headline} from this backup. Settings are restored too. This can't be undone.",
        style = AppTheme.type.body, color = c.ink,
    )
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetButton("Cancel", fill = null, content = c.ink, onClick = onCancel, modifier = Modifier.weight(1f))
        SheetButton("Replace", fill = c.danger, content = Color.White, onClick = { onConfirm(b) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SheetButton(
    text: String,
    fill: Color?,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    quiet: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val base = modifier.heightIn(min = 48.dp).pressScale(source, 0.97f)
    val surface = when {
        fill != null -> base.clip(Shapes.full).background(fill)
        quiet -> base.clip(Shapes.full)
        else -> base.glassControl(Shapes.full).clip(Shapes.full)
    }
    Box(
        surface.clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.label, color = content, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
    }
}
