package com.timebox.android.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.theme.TimeboxTheme

// Issue 296 round 4: several Assistant Cards in one conversation. Sample data only.

private enum class Stacking(val label: String) { Stacked("Stacked"), Carousel("Swipe between") }

private sealed interface SampleCard {
    val label: String
    data class Blocks(val day: SampleDay, val lane: LaneMode, override val label: String) : SampleCard
    data class Types(val sample: TypeSample, val lane: LaneMode, override val label: String) : SampleCard
}

private data class Turn(val question: String, val cards: List<SampleCard>, val answer: String)

private fun turns(): List<Turn> {
    val review = sample(DayScenario.Review)
    fun weekday(offset: Long, drop: Set<Int>) = review.copy(
        date = TODAY.minusDays(offset), relative = null,
        blocks = review.blocks.filter { it.id !in drop },
    )
    return listOf(
        Turn("How did yesterday go against my plan?",
            listOf(SampleCard.Blocks(review, LaneMode.Both, "Yesterday")),
            "Most of it went to plan. Deep Work started late and ran 25m short, YouTube filled the gap before lunch, and Gym didn’t happen."),
        Turn("And last week by Task Type?",
            listOf(SampleCard.Types(typeSample(TypeScenario.LastWeek), LaneMode.Both, "Last week")),
            "Work came in 45m under plan and Leisure 4h 50m over. Sleep isn’t planned, so it has no difference."),
        Turn("Compare what I actually did on Tuesday, Wednesday and Thursday.",
            listOf(
                SampleCard.Blocks(weekday(5, setOf(15, 18)), LaneMode.Actual, "Tuesday"),
                SampleCard.Blocks(weekday(4, setOf(13, 17)), LaneMode.Actual, "Wednesday"),
                SampleCard.Blocks(weekday(3, setOf(19)), LaneMode.Actual, "Thursday"),
            ),
            "Tuesday was your steadiest Deep Work day. Wednesday lost the morning block entirely, and Thursday’s afternoon session was the only one that ran long."),
        Turn("Show tomorrow’s plan and how this week is going.",
            listOf(
                SampleCard.Blocks(sample(DayScenario.Future), LaneMode.Planned, "Tomorrow"),
                SampleCard.Types(typeSample(TypeScenario.ThisWeek), LaneMode.Both, "This week"),
            ),
            "Tomorrow is front-loaded with Deep Work. This week so far, Work is behind plan and Leisure is ahead."),
    )
}

@Composable
internal fun CardConversationPrototype(layout: String, onKind: () -> Unit) {
    var stacking by remember { mutableStateOf(Stacking.Stacked) }
    var opened by remember { mutableStateOf<String?>(null) }
    val colors = TimeboxTheme.colors
    val ink = Color(0xFF1F1A00)
    val lanes = DayLayout.entries.firstOrNull { it.arg == layout } ?: DayLayout.Lanes
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(Color(0xFFFFE066)).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PROTOTYPE · Several cards", Modifier.weight(1f), style = TimeboxTheme.type.kicker, color = ink)
                Text("→ Block Card", Modifier.clickable(onClick = onKind).padding(4.dp), style = TimeboxTheme.type.bodySmall, color = ink, fontWeight = FontWeight.Medium)
            }
            Pills(Stacking.entries, stacking, { "Cards: " + it.label }, { stacking = it }, ink)
            opened?.let { Text(it, style = TimeboxTheme.type.bodySmall, color = ink) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Assistant", Modifier.semantics { heading() }, style = TimeboxTheme.type.screenTitle, color = colors.on)
        }
        HorizontalDivider(color = colors.hairline)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            turns().forEach { turn ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.padding(start = 56.dp, end = 20.dp).align(Alignment.End), color = colors.card, shape = RoundedCornerShape(16.dp, 16.dp, 3.dp, 16.dp), border = BorderStroke(1.dp, colors.hairline)) {
                        Text(turn.question, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), style = TimeboxTheme.type.body)
                    }
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = colors.onVariant)
                        Text("Assistant", style = TimeboxTheme.type.bodySmall, color = colors.onVariant)
                    }
                    Cards(turn.cards, stacking, lanes) { opened = it }
                    Text(turn.answer, Modifier.padding(horizontal = 20.dp), style = TimeboxTheme.type.body.copy(fontSize = 15.sp, lineHeight = 25.sp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), color = colors.field, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, colors.hairline)) {
            Text("Ask a follow-up…", Modifier.padding(horizontal = 18.dp, vertical = 14.dp), style = TimeboxTheme.type.body, color = colors.onVariant)
        }
    }
}

@Composable
private fun OneCard(card: SampleCard, lanes: DayLayout, onOpen: (String) -> Unit) {
    when (card) {
        is SampleCard.Blocks -> DayCard(card.day, card.lane, lanes) { onOpen("Would open Day · ${card.label}") }
        is SampleCard.Types -> TypeCard(card.sample, card.lane, TypeBoth.PairedBars, TypeDetail.Total) { onOpen("Would open Trends · ${card.sample.range}") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Cards(cards: List<SampleCard>, stacking: Stacking, lanes: DayLayout, onOpen: (String) -> Unit) {
    val colors = TimeboxTheme.colors
    if (cards.size == 1 || stacking == Stacking.Stacked) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.forEach { OneCard(it, lanes, onOpen) }
        }
        return
    }
    val pager = rememberPagerState { cards.size }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cards.forEachIndexed { index, card ->
                val on = pager.currentPage == index
                Surface(color = if (on) colors.on else colors.bg, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, colors.hairline)) {
                    Text(card.label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = TimeboxTheme.type.bodySmall, color = if (on) colors.bg else colors.onVariant)
                }
            }
        }
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 10.dp, verticalAlignment = Alignment.Top) { page ->
            OneCard(cards[page], lanes, onOpen)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            cards.indices.forEach { i ->
                Box(Modifier.padding(3.dp).size(6.dp).background(if (i == pager.currentPage) colors.on else colors.hairline, CircleShape))
            }
        }
    }
}
