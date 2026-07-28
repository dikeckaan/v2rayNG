package com.v2ray.ang.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactRoundTest {

    // ==================== Detection ====================

    /** The actual target device: MU5358, 240x240 @ 160dpi, reports notround. */
    @Test
    fun test_detects_mu5358_240x240_reporting_notround() {
        assertTrue(
            isCompactRoundScreen(
                smallestScreenWidthDp = 240,
                screenWidthDp = 240,
                screenHeightDp = 240,
                isScreenRound = false
            )
        )
    }

    @Test
    fun test_ignores_typical_phone() {
        assertFalse(
            isCompactRoundScreen(
                smallestScreenWidthDp = 411,
                screenWidthDp = 411,
                screenHeightDp = 891,
                isScreenRound = false
            )
        )
    }

    @Test
    fun test_ignores_small_but_tall_screen() {
        // Small width alone must not trigger compact mode — it has to be near-square.
        assertFalse(
            isCompactRoundScreen(
                smallestScreenWidthDp = 240,
                screenWidthDp = 240,
                screenHeightDp = 400,
                isScreenRound = false
            )
        )
    }

    @Test
    fun test_detects_device_that_reports_round_honestly() {
        assertTrue(
            isCompactRoundScreen(
                smallestScreenWidthDp = 227,
                screenWidthDp = 227,
                screenHeightDp = 227,
                isScreenRound = true
            )
        )
    }

    @Test
    fun test_tolerates_small_aspect_delta() {
        // 8dp of slack, e.g. a 240x246 panel.
        assertTrue(
            isCompactRoundScreen(
                smallestScreenWidthDp = 240,
                screenWidthDp = 240,
                screenHeightDp = 246,
                isScreenRound = false
            )
        )
    }

    @Test
    fun test_rejects_just_above_threshold() {
        assertFalse(
            isCompactRoundScreen(
                smallestScreenWidthDp = 281,
                screenWidthDp = 281,
                screenHeightDp = 281,
                isScreenRound = false
            )
        )
    }

    // ==================== Chord math ====================

    @Test
    fun test_chordHalfWidth_atCenter_equalsRadius() {
        assertEquals(120f, chordHalfWidth(120f, 0f), 0.01f)
    }

    @Test
    fun test_chordHalfWidth_atEdge_isZero() {
        assertEquals(0f, chordHalfWidth(120f, 120f), 0.01f)
    }

    @Test
    fun test_chordHalfWidth_beyondEdge_isZero() {
        assertEquals(0f, chordHalfWidth(120f, 200f), 0.01f)
    }

    @Test
    fun test_chordHalfWidth_isSymmetric() {
        assertEquals(chordHalfWidth(120f, 60f), chordHalfWidth(120f, -60f), 0.01f)
    }

    @Test
    fun test_chordHalfWidth_knownValues() {
        // sqrt(120^2 - 60^2)  = sqrt(10800) ~= 103.92
        assertEquals(103.92f, chordHalfWidth(120f, 60f), 0.01f)
        // sqrt(120^2 - 100^2) = sqrt(4400)  ~=  66.33
        assertEquals(66.33f, chordHalfWidth(120f, 100f), 0.01f)
    }

    /**
     * Ties the two safe-area strategies together: the strict inscribed square must
     * actually fit inside the circle. On a 240dp screen the exact inset is 35.15dp;
     * rounding it down to 35 gives an 85dp half-side against an 84.7dp chord, i.e.
     * corners outside the panel. circularStrictSafeArea rounds up for that reason.
     */
    @Test
    fun test_strictInsetSquareCornerIsInsideCircle() {
        // Rounded up: 36dp inset -> 84dp half-side, chord at 84dp is ~85.7dp. Fits.
        assertEquals(85.70f, chordHalfWidth(120f, 84f), 0.01f)
        assertTrue(84f <= chordHalfWidth(120f, 84f))

        // Rounded down: 35dp inset -> 85dp half-side, chord at 85dp is ~84.71dp. Does not.
        assertEquals(84.71f, chordHalfWidth(120f, 85f), 0.01f)
        assertFalse(85f <= chordHalfWidth(120f, 85f))
    }
}
