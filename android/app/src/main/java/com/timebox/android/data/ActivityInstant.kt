package com.timebox.android.data

import java.time.Instant
import java.time.OffsetDateTime

/** Android's older Instant parser only accepts Z; API timestamps may carry a numeric offset. */
fun parseActivityInstant(value: String): Instant = OffsetDateTime.parse(value).toInstant()
