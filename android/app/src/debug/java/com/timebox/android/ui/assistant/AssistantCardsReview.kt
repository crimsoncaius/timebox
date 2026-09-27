package com.timebox.android.ui.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.*

/** Deterministic review data for the production card renderer; never sent to the API. */
internal fun reviewCards(): List<AssistantCard> {
    val base = AssistantCard("blocks", LocalDate.parse("2026-09-26"), LocalDate.parse("2026-09-26"),
        ZoneId.of("Asia/Singapore"), Instant.parse("2026-09-26T02:45:00Z"), "both", null, false,
        blocks = listOf(
            AssistantBlock("actual", Instant.parse("2026-09-25T15:00:00Z"), Instant.parse("2026-09-25T23:00:00Z"), false, 480, 420, "Sleep", "Rest/Sleep"),
            AssistantBlock("planned", Instant.parse("2026-09-26T01:00:00Z"), Instant.parse("2026-09-26T02:00:00Z"), false, 60, 60, "Write proposal", "Work/Writing"),
            AssistantBlock("actual", Instant.parse("2026-09-26T01:15:00Z"), Instant.parse("2026-09-26T02:45:00Z"), true, 90, 90, "Write proposal", "Work/Writing"),
        ))
    return listOf(base, base.copy(id = "types", date = LocalDate.parse("2026-09-20"), filter = "Work", blocks = emptyList(), types = listOf(
        AssistantType("Work", 7200.0, 9000.0), AssistantType("Work/Writing", 7200.0, 5400.0), AssistantType("Work/Review", 0.0, 3600.0))),
        base.copy(id = "empty", date = LocalDate.parse("2026-09-19"), endDate = LocalDate.parse("2026-09-19"), blocks = emptyList(), types = emptyList()))
}

@Composable
internal fun AssistantCardsReview() {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Assistant card review · sample data")
        AssistantCards(reviewCards(), {}, { _, _ -> })
        Text("Production cards, fixed at their read time. Swipe or choose a date tab.")
    }
}
