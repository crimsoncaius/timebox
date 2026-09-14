package com.timebox.android.ui.day

import com.timebox.android.data.SLOT_MINUTES
import kotlin.math.floor
import kotlin.math.roundToInt

internal const val BLOCK_INTERACTION_STEP_MINUTES = 5

internal fun snapToBlockInteractionStep(minutes: Float): Int =
    (minutes / BLOCK_INTERACTION_STEP_MINUTES).roundToInt() * BLOCK_INTERACTION_STEP_MINUTES

/** Only the initial tap request uses half-hours; placement resolution and edits stay precise. */
internal fun initialBlockStart(minutes: Float, visibleStart: Int, visibleEnd: Int): Int =
    (floor(minutes / SLOT_MINUTES).toInt() * SLOT_MINUTES)
        .coerceIn(visibleStart, visibleEnd - SLOT_MINUTES)
