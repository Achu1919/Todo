package dev.saikrishna.todo.ui.settings

import dev.saikrishna.todo.ui.components.GlassLevel
import dev.saikrishna.todo.ui.components.glassSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import dev.saikrishna.todo.data.ImportMode
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.saikrishna.todo.reminders.Reminders
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saikrishna.todo.data.AppSettings
import dev.saikrishna.todo.data.ThemeMode
import dev.saikrishna.todo.ui.components.CircleIconButton
import dev.saikrishna.todo.ui.components.skyBackdrop
import androidx.compose.foundation.border
import dev.saikrishna.todo.ui.components.MotionSwitch
import dev.saikrishna.todo.ui.components.SegmentedPill
import dev.saikrishna.todo.ui.home.TodoViewModel
import dev.saikrishna.todo.ui.icons.AppIcons
import dev.saikrishna.todo.ui.motion.rememberSystemAnimationsOff
import dev.saikrishna.todo.ui.theme.AppTheme
import dev.saikrishna.todo.ui.theme.Shapes
import dev.saikrishna.todo.ui.theme.Spacing

@Composable
fun SettingsScreen(
    vm: TodoViewModel,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenLists: () -> Unit = {},
    onOpenTemplates: () -> Unit = {},
) {
    val c = AppTheme.colors
    val stored by vm.settings.collectAsStateWithLifecycle()
    val settings = stored ?: AppSettings()
    val systemOff = rememberSystemAnimationsOff()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    // v0.10 Data section: import sheet state + the last export status line.
    var importStep by remember { mutableStateOf<ImportStep?>(null) }
    var dataStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val backup = (context.applicationContext as? dev.saikrishna.todo.TodoApp)?.backup

    Box(Modifier.fillMaxSize()) {
    ScreenScaffold(title = "Settings", onBack = onBack) {
        GroupLabel("Motion")
        Card {
            SwitchRow(
                title = "Reduce motion",
                subtitle = if (systemOff) "System animations are off, so motion is already reduced."
                else "Swap springs and slides for short crossfades.",
                checked = settings.reduceMotion,
                onChange = { vm.setReduceMotion(it) },
            )
            Divider()
            SwitchRow(
                title = "Haptics",
                subtitle = "Ticks, thumps and the all-done rise.",
                checked = settings.haptics,
                onChange = { vm.setHaptics(it) },
            )
        }

        GroupLabel("Organise")
        Card {
            val lists by vm.lists.collectAsStateWithLifecycle()
            val templates by vm.templates.collectAsStateWithLifecycle()
            LinkRow(
                title = "Lists",
                subtitle = lists.joinToString(" · ") { it.name }.ifEmpty { "Personal · Work" },
                onClick = onOpenLists,
            )
            Divider()
            LinkRow(
                title = "Templates",
                subtitle = if (templates.isEmpty()) "None yet" else templates.joinToString(" · ") { it.name },
                onClick = onOpenTemplates,
            )
        }

        GroupLabel("Focus")
        FocusScheduleCard(vm)

        GroupLabel("Planning")
        PlanningCard(vm)

        GroupLabel("Appearance")
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Theme", style = AppTheme.type.title, color = c.ink)
                Spacer(Modifier.height(12.dp))
                SegmentedPill(
                    options = ThemeMode.entries.toList(),
                    selected = settings.theme,
                    label = { it.name },
                    onSelect = { vm.setTheme(it) },
                )
            }
            Divider()
            // v0.12: where the app opens, and the optional name in the Overview greeting.
            Column(Modifier.padding(16.dp)) {
                Text("Open app on", style = AppTheme.type.title, color = c.ink)
                Spacer(Modifier.height(12.dp))
                SegmentedPill(
                    options = dev.saikrishna.todo.data.StartPage.entries.toList(),
                    selected = settings.openOn,
                    label = { it.name },
                    onSelect = { vm.setOpenOn(it) },
                )
            }
            Divider()
            Column(Modifier.padding(16.dp)) {
                Text("Your name", style = AppTheme.type.title, color = c.ink)
                Text("Shown in the Overview greeting. Optional.", style = AppTheme.type.meta, color = c.inkSecondary)
                Spacer(Modifier.height(12.dp))
                val nameState = androidx.compose.foundation.text.input.rememberTextFieldState(settings.name)
                androidx.compose.runtime.LaunchedEffect(nameState) {
                    androidx.compose.runtime.snapshotFlow { nameState.text.toString() }
                        .collect { if (it.trim() != vm.settings.value?.name) vm.setGreetingName(it) }
                }
                Field(nameState, "First name", Modifier.fillMaxWidth()) { }
            }
        }

        GroupLabel("Reminders")
        Card {
            // Re-read on resume: the user may have just changed these in system settings.
            var notifications by remember { mutableStateOf(Reminders.notificationsAllowed(context)) }
            var exact by remember { mutableStateOf(Reminders.canExact(context)) }
            LifecycleResumeEffect(Unit) {
                notifications = Reminders.notificationsAllowed(context)
                exact = Reminders.canExact(context)
                onPauseOrDispose { }
            }
            LinkRow(
                title = "Notifications",
                subtitle = if (notifications) "On. Timed tasks remind you at their time." else "Off. Timed tasks won't alert you.",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            Divider()
            LinkRow(
                title = "Exact timing",
                subtitle = if (exact) "On. Reminders arrive on the minute."
                else "Off. Reminders may arrive a few minutes late.",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }

        GroupLabel("Data")
        DataCard(onStep = { importStep = it }, status = dataStatus, onStatus = { dataStatus = it })

        GroupLabel("About")
        Card {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Version", style = AppTheme.type.title, color = c.ink, modifier = Modifier.weight(1f))
                Text(version, style = AppTheme.type.mono, color = c.inkSecondary)
            }
            Divider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onOpenLicenses)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Licences", style = AppTheme.type.title, color = c.ink, modifier = Modifier.weight(1f))
                Icon(AppIcons.ChevronRight, null, tint = c.inkTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
    ImportSheet(
        step = importStep,
        onMerge = { b -> if (backup != null) scope.launch { runImport(backup, b, ImportMode.Merge) { importStep = it } } },
        onReplace = { b, current -> importStep = ImportStep.ConfirmReplace(b, current) },
        onConfirmReplace = { b -> if (backup != null) scope.launch { runImport(backup, b, ImportMode.Replace) { importStep = it } } },
        onDismiss = { if (importStep != ImportStep.Working) importStep = null },
    )
    }
}

/** Shared chrome for pushed secondary screens: back button + serif title, scrolling body. */
@Composable
fun ScreenScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxSize().background(c.paper).skyBackdrop(c).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(AppIcons.ArrowLeft, "Back", onBack, glass = true)
            Spacer(Modifier.width(14.dp))
            Text(title, style = AppTheme.type.display, color = c.ink)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            content = content,
        )
    }
}

@Composable
internal fun GroupLabel(text: String) {
    Text(
        text,
        style = AppTheme.type.label,
        color = AppTheme.colors.inkSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glassSurface(Shapes.xl, GlassLevel.Low, AppTheme.colors.glassCard).clip(Shapes.xl),
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

@Composable
internal fun Divider() {
    Spacer(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(AppTheme.colors.inkHairline))
}

@Composable
internal fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = AppTheme.type.title, color = c.ink)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = AppTheme.type.meta, color = c.inkSecondary)
        }
        Icon(AppIcons.ChevronRight, null, tint = c.inkTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = AppTheme.type.title, color = c.ink)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = AppTheme.type.meta, color = c.inkSecondary)
        }
        MotionSwitch(checked = checked, onCheckedChange = onChange, label = title)
    }
}

/** v0.8: the optional morning "Plan your day" nudge (off by default, 8:00 when turned on). */
@Composable
private fun PlanningCard(vm: TodoViewModel) {
    val prefs by vm.planPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    Card {
        SwitchRow(
            title = "Plan your day",
            subtitle = if (prefs.enabled) "A nudge at ${dev.saikrishna.todo.domain.QuickParse.timeLabel(prefs.minute)} to sort overdue and today's tasks."
            else "Off. You can still plan from the list menu on Today.",
            checked = prefs.enabled,
            onChange = { on ->
                vm.setPlan(prefs.copy(enabled = on))
                if (on && !Reminders.notificationsAllowed(context)) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            },
        )
        if (prefs.enabled) {
            Divider()
            LinkRow(title = "Time", subtitle = dev.saikrishna.todo.domain.QuickParse.timeLabel(prefs.minute), onClick = { picking = true })
        }
    }
    if (picking) {
        dev.saikrishna.todo.ui.panel.TimePickDialog(
            initialMinute = prefs.minute,
            onSet = { m -> picking = false; vm.setPlan(prefs.copy(minute = m)) },
            onDismiss = { picking = false },
        )
    }
}
