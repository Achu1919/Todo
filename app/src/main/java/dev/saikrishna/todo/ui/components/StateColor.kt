package dev.saikrishna.todo.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * v0.9.1: a colour that animates when [on] changes but follows a THEME change instantly (the
 * progress animates, the two end colours are read fresh). animateColorAsState tweened every
 * themed colour after a light/dark switch, showing a third state under the theme reveal.
 */
@Composable
fun animateStateColor(on: Boolean, onColor: Color, offColor: Color, spec: AnimationSpec<Float>, label: String = "state colour"): State<Color> {
    val k by animateFloatAsState(if (on) 1f else 0f, spec, label = label)
    val a by rememberUpdatedState(offColor)
    val b by rememberUpdatedState(onColor)
    return remember { derivedStateOf { lerp(a, b, k.coerceIn(0f, 1f)) } }
}
