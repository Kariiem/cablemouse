package com.cablemouse.ui

/** Timings shared by every touch surface. */
object Gesture {
    /** A tap is shorter than this and never travels more than [TAP_SLOP_DP]. */
    const val TAP_MS = 250L
    const val TAP_SLOP_DP = 10

    /** Touching again within this time after a tap starts a drag (button held until the finger lifts). */
    const val DRAG_WINDOW_MS = 300L
}
