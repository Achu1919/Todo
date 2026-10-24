package dev.saikrishna.todo.ui.home

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performSemanticsAction

/**
 * v0.13 navigation for tests: the header pill is Home · Todo · Calendar, the four Todo views sit
 * behind the bottom-left view switcher, and Settings is the profile circle on Home ("home-settings").
 */
object NavTest {
    private fun role(r: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, r)
    fun sectionTab(s: Section) = hasText(s.title) and role(Role.Tab) and !hasTestTag("switcher-${s.name.lowercase()}")

    fun section(rule: ComposeTestRule, s: Section, settle: () -> Unit) {
        rule.onNode(sectionTab(s)).performSemanticsAction(SemanticsActions.OnClick); settle()
    }

    /** Any page: Home/Calendar by the header pill, a Todo view through the switcher. */
    fun go(rule: ComposeTestRule, f: Filter, settle: () -> Unit) {
        when (f) {
            Filter.Overview -> section(rule, Section.Home, settle)
            Filter.Calendar -> section(rule, Section.Calendar, settle)
            else -> {
                rule.onNode(hasTestTag("view-switcher"), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick); settle()
                rule.onNode(hasTestTag("switcher-${f.name.lowercase()}")).performSemanticsAction(SemanticsActions.OnClick); settle()
            }
        }
    }

    /** Settings: Home's profile circle (the push starts at the tap). */
    fun openSettings(rule: ComposeTestRule, settle: () -> Unit) {
        section(rule, Section.Home, settle)
        rule.onNode(hasTestTag("home-settings")).performSemanticsAction(SemanticsActions.OnClick)
    }
}
