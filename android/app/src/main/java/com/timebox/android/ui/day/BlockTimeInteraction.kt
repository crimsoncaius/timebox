package com.timebox.android.ui.day

import kotlin.math.roundToInt

internal const val BLOCK_INTERACTION_STEP_MINUTES = 5

internal fun snapToBlockInteractionStep(minutes: Float): Int =
    (minutes / BLOCK_INTERACTION_STEP_MINUTES).roundToInt() * BLOCK_INTERACTION_STEP_MINUTES
