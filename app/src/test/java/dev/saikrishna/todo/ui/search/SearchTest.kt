package dev.saikrishna.todo.ui.search

import dev.saikrishna.todo.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val tasks = listOf(
        Task(id = 1, title = "Buy milk", createdAt = 1),
        Task(id = 2, title = "Call mum", notes = "about the MILK delivery", createdAt = 2),
        Task(id = 3, title = "Milk the deadline", done = true, createdAt = 3, completedAt = 10),
        Task(id = 4, title = "Old milk", done = true, createdAt = 4, completedAt = 20),
        Task(id = 5, title = "Gym", createdAt = 5),
    )

    @Test fun blankQueryReturnsNothing() {
        assertTrue(searchTasks(tasks, "  ").isEmpty())
    }

    @Test fun matchesTitleAndNotesCaseInsensitivelyOpenFirst() {
        assertEquals(listOf(1L, 2L, 4L, 3L), searchTasks(tasks, " milk ").map { it.id })
    }

    @Test fun noMatch() {
        assertTrue(searchTasks(tasks, "zebra").isEmpty())
    }
}
