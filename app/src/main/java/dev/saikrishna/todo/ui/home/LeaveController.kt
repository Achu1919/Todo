package dev.saikrishna.todo.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives rows that are leaving a page: Dwell (wall-clock timer) → Collapsing (the row's own exit
 * animation) → removed. The timer lives here, not in the row, so it survives the row being
 * scrolled off-screen or the page being switched; an off-screen row skips the collapse.
 */
@Stable
class LeaveController(
    private val scope: CoroutineScope,
    private val dwellMillis: () -> Long,
    private val isOnScreen: (id: Long, page: Filter) -> Boolean,
) {
    val entries: SnapshotStateMap<Long, LeaveEntry> = mutableStateMapOf()
    private val jobs = HashMap<Long, Job>()

    fun phase(id: Long): LeavePhase? = entries[id]?.phase

    fun start(id: Long, from: Filter) {
        jobs.remove(id)?.cancel()
        entries[id] = LeaveEntry(from, LeavePhase.Dwell)
        jobs[id] = scope.launch {
            delay(dwellMillis())
            jobs.remove(id)
            if (isOnScreen(id, from)) entries[id] = LeaveEntry(from, LeavePhase.Collapsing) else entries.remove(id)
        }
    }

    /** User reversed the change during the dwell: the row simply stays. */
    fun cancel(id: Long) {
        jobs.remove(id)?.cancel()
        entries.remove(id)
    }

    /** The row finished collapsing (or left composition while collapsing). */
    fun finish(id: Long) {
        jobs.remove(id)?.cancel()
        entries.remove(id)
    }
}
