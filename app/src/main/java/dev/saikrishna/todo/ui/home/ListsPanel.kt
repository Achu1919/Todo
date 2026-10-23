package dev.saikrishna.todo.ui.home

import androidx.compose.ui.unit.dp

// v0.13: the v0.9 lists panel is gone. The bottom-left button is the view switcher
// (ViewSwitcher.kt); Plan and Settings live on Home, list management in Home's Lists card and
// Settings > Lists. Only the shared measurements remain.

/** v0.9: the bottom-left button is the composer's height (48 dp single-line capsule). */
val ListsButtonSize = 48.dp
/** v0.9: gap between that button and the composer, expanded and collapsed. */
val ListsButtonGap = 8.dp
