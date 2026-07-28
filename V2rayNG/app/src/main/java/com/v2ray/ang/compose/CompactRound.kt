package com.v2ray.ang.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Support for small, physically round screens.
 *
 * The target device (MU5358, 240x240 @ 160dpi) reports `notround`, so neither the
 * `-round` resource qualifier nor `Configuration.isScreenRound` nor system-provided
 * round insets are available. The circular safe area is computed here instead.
 */

/** Screens at or below this smallest-width are treated as compact. */
const val COMPACT_ROUND_MAX_SW_DP = 280

/** How far from square a screen may be and still count as round. */
const val COMPACT_ROUND_MAX_ASPECT_DELTA_DP = 8

/**
 * Inset fraction that yields the largest square fitting inside a circle:
 * (1 - 1/sqrt(2)) / 2 ~= 0.14645. On a 240dp screen that is ~35dp per side,
 * leaving a 170dp square.
 */
private val STRICT_INSET_FRACTION: Float = (1f - 1f / sqrt(2f)) / 2f

/**
 * Whether a screen should use the compact round layout.
 *
 * Pure function so it can be unit tested without a Configuration instance.
 */
fun isCompactRoundScreen(
    smallestScreenWidthDp: Int,
    screenWidthDp: Int,
    screenHeightDp: Int,
    isScreenRound: Boolean,
): Boolean {
    val small = smallestScreenWidthDp <= COMPACT_ROUND_MAX_SW_DP
    val nearlySquare =
        abs(screenWidthDp - screenHeightDp) <= COMPACT_ROUND_MAX_ASPECT_DELTA_DP
    return (small && nearlySquare) || isScreenRound
}

/**
 * Half the width of the horizontal chord of a circle at a given vertical distance
 * from its centre: sqrt(r^2 - d^2), clamped to zero outside the circle.
 *
 * Used to size content that sits away from the vertical centre of a round screen.
 */
fun chordHalfWidth(radius: Float, distanceFromCenter: Float): Float {
    val d = abs(distanceFromCenter)
    if (d >= radius) return 0f
    return sqrt(radius * radius - d * d)
}

/** True when the current screen uses the compact round layout. Provided by [AppTheme]. */
val LocalCompactRound = staticCompositionLocalOf { false }

/** Reads the current [android.content.res.Configuration] and applies [isCompactRoundScreen]. */
@Composable
@ReadOnlyComposable
fun currentIsCompactRound(): Boolean {
    val configuration = LocalConfiguration.current
    return isCompactRoundScreen(
        smallestScreenWidthDp = configuration.smallestScreenWidthDp,
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
        isScreenRound = configuration.isScreenRound,
    )
}

/**
 * Constrains content to the largest square that fits inside the circular display.
 *
 * A gentler inset is not enough: at 20dp the corner of a 240dp screen still sits
 * ~141dp from the centre, outside the 120dp radius, so it is physically invisible.
 */
fun Modifier.circularStrictSafeArea(): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val inset = (minOf(width, height) * STRICT_INSET_FRACTION).roundToInt()
    val innerWidth = (width - 2 * inset).coerceAtLeast(0)
    val innerHeight = (height - 2 * inset).coerceAtLeast(0)

    val placeable = measurable.measure(
        Constraints(
            minWidth = 0,
            maxWidth = innerWidth,
            minHeight = 0,
            maxHeight = innerHeight,
        )
    )

    layout(width, height) { placeable.place(inset, inset) }
}
