package dev.saikrishna.todo.ui.home

import androidx.compose.runtime.Stable

/**
 * Decides which rows play the new-task entrance (spec 6.4). Rows present at first load form a
 * baseline and never animate; any id seen for the first time afterwards animates exactly once.
 * [forget] re-arms ids (undo re-inserts a row with the entrance, spec 6.6).
 */
@Stable
class EntranceTracker {
    private val seen = HashSet<Long>()
    private var baselined = false

    fun baseline(ids: Collection<Long>) {
        if (baselined) return
        seen.addAll(ids)
        baselined = true
    }

    fun forget(ids: Collection<Long>) {
        seen.removeAll(ids.toSet())
    }

    private val delays = HashMap<Long, Int>()
    private var staggerLeft = 0
    private var staggerIndex = 0
    private var staggerStep = 0

    /**
     * v0.7 template use: the next [count] entrances are staggered by [stepMs] each, in the order
     * they are claimed (= list order, top to bottom). Armed BEFORE the insert, so it never races
     * the database emission.
     */
    fun armStagger(count: Int, stepMs: Int = 70) {
        staggerLeft = count; staggerIndex = 0; staggerStep = stepMs
    }

    fun delayFor(id: Long): Int = delays.remove(id) ?: 0

    /** true once per id that appeared after the baseline. */
    fun claimEntrance(id: Long): Boolean {
        val claimed = baselined && seen.add(id)
        if (claimed && staggerLeft > 0) {
            delays[id] = staggerIndex++ * staggerStep
            staggerLeft--
        }
        return claimed
    }
}
