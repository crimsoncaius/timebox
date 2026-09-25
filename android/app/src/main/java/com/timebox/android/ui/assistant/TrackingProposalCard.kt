package com.timebox.android.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.data.identityText
import com.timebox.android.data.parseActivityInstant
import com.timebox.android.data.remote.ActivitySnapshotDto
import com.timebox.android.data.remote.ActualBlockDto
import com.timebox.android.ui.theme.TimeboxShapes
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ProposalCardActions(
    val onConfirm: (TrackingProposal, ProposalView) -> Unit,
    val onDismiss: (TrackingProposal) -> Unit,
    val onChange: (TrackingProposal) -> Unit,
    val onChoose: (TrackingProposal, Int) -> Unit,
    val onViewDay: (TrackingProposal, ActualBlockDto) -> Unit,
)

/** A Tracking Proposal in the conversation: live while pending, then Applied, Expired or Dismissed. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrackingProposalCard(
    p: TrackingProposal, state: ProposalState, snapshot: ActivitySnapshotDto?, now: Instant, zone: ZoneId,
    busy: Boolean, error: String?, actions: ProposalCardActions,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val expired = state.status == ProposalStatus.Pending && now >= p.expiresAt
    val pending = state.status == ProposalStatus.Pending && !expired
    val view = snapshot?.let { deriveProposal(p, state, it, now, zone) }
    val live = pending && view != null && view.invalid == null
    val applied = if (state.status == ProposalStatus.Applied) appliedLine(state, snapshot, now, zone) else null
    val running = applied?.second?.endAt == null && applied?.second != null
    Surface(color = colors.field, shape = TimeboxShapes.card, border = BorderStroke(1.dp, if (live) colors.actualBorder else colors.hairline)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (live || running) colors.actual else colors.onVariant.copy(alpha = .4f)))
                Text(when {
                    state.status == ProposalStatus.Applied -> if (state.stopped) "STOPPED" else "TRACKED"
                    state.status == ProposalStatus.Dismissed -> "DISMISSED"
                    expired -> "EXPIRED"
                    view == null -> "TRACKING"
                    view.invalid != null -> "CAN'T ${view.action.label.uppercase()}"
                    else -> view.action.label.uppercase()
                }, Modifier.weight(1f).semantics { heading() }, style = type.kicker, color = colors.onVariant)
                val left = Duration.between(now, p.expiresAt)
                if (live && left <= Duration.ofMinutes(5)) Text("Expires in ${left.toMinutes() + 1} min", style = type.bodySmall, color = colors.onVariant)
            }
            val title = view?.title ?: if (p.stop) "Stop tracking" else p.taskTypes.singleOrNull()?.path ?: "Choose an activity"
            Text(if (pending) title else (state.chosen?.let { id -> p.taskTypes.find { it.id == id }?.path } ?: title),
                style = type.sectionTitle, color = if (pending) colors.on else colors.onVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when {
                pending && view != null -> PendingBody(p, state, view, snapshot!!, now, zone, busy, error, actions)
                pending -> Text("Connect once to load Activity Tracking.", style = type.bodySmall, color = colors.onVariant)
                applied != null -> {
                    Text(applied.first, style = type.mono.copy(fontSize = 13.sp), color = colors.onVariant)
                    applied.second?.let { record ->
                        TextButton(onClick = { actions.onViewDay(p, record) }, contentPadding = PaddingValues(0.dp)) {
                            Text("View in Day", style = type.button)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
                        }
                    }
                }
                else -> Text(if (expired) "Expired · not applied" else "Dismissed · not applied", style = type.mono.copy(fontSize = 13.sp), color = colors.onVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingBody(
    p: TrackingProposal, state: ProposalState, view: ProposalView, snapshot: ActivitySnapshotDto,
    now: Instant, zone: ZoneId, busy: Boolean, error: String?, actions: ProposalCardActions,
) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    val chosen = state.chosen ?: p.taskTypes.singleOrNull()?.id
    // Chips stay until confirmation so a wrong pick is corrected in place.
    if (p.taskTypes.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        p.taskTypes.forEach { option ->
            FilterChip(selected = chosen == option.id, onClick = { actions.onChoose(p, option.id) }, enabled = !busy,
                label = { Text(option.path.substringAfterLast('/')) })
        }
    }
    if (view.invalid != null) Text(view.invalid, style = type.body, color = colors.onVariant)
    else if (chosen != null || p.stop) {
        Text(when {
            p.stop -> "at ${clockLabel(p.at ?: now, zone)}"
            p.at == null -> "from now"
            else -> "from ${clockLabel(p.at, zone)} · ${elapsedClock(Duration.between(p.at, now))} so far"
        }, style = type.mono.copy(fontSize = 13.sp), color = colors.on)
        if (view.reachesBack && p.at != null) ReplacementStrip(p.at, view.title.orEmpty(), snapshot, now, zone)
        view.impact.forEach { Text(it, style = type.bodySmall, color = colors.onVariant) }
    }
    error?.let { Text(it, style = type.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { actions.onDismiss(p) }, enabled = !busy) { Text("Dismiss", style = type.button) }
        if (!p.stop && chosen != null && view.invalid == null) TextButton(onClick = { actions.onChange(p) }, enabled = !busy) { Text("Change", style = type.button) }
        Spacer(Modifier.weight(1f))
        if (view.invalid == null) Button(onClick = { actions.onConfirm(p, view) }, enabled = !busy && (p.stop || chosen != null),
            shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 44.dp)) {
            Text(if (busy) view.action.progress else view.action.label, style = type.button)
        }
    }
}

private fun elapsedClock(duration: Duration): String = duration.seconds.coerceAtLeast(0).let { "%d:%02d:%02d".format(it / 3600, it / 60 % 60, it % 60) }

/** Now/After over the affected window, echoing the switch sheet's preview. Shown only when history is replaced. */
@Composable
private fun ReplacementStrip(at: Instant, next: String, snapshot: ActivitySnapshotDto, now: Instant, zone: ZoneId) {
    val colors = TimeboxTheme.colors
    val type = TimeboxTheme.type
    fun end(r: ActualBlockDto) = r.endAt?.let(::parseActivityInstant) ?: now
    // Frame the change itself: a long earlier block must not squeeze the replaced span into a sliver.
    val from = at.minus(maxOf(Duration.ofMinutes(10), Duration.between(at, now).dividedBy(2)))
    val to = now.plus(Duration.ofMinutes(5))
    val span = Duration.between(from, to).seconds.toFloat()
    fun fraction(t: Instant) = (Duration.between(from, t).seconds / span).coerceIn(0f, 1f)
    val visible = snapshot.records.filter { end(it) > from }
    @Composable fun Strip(label: String, pieces: List<Triple<Instant, Instant, Pair<String, Boolean>>>) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(44.dp), style = type.bodySmall, color = colors.onVariant)
            BoxWithConstraints(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(4.dp)).background(colors.low)) {
                val width = maxWidth
                pieces.forEach { (start, stop, info) ->
                    val (name, highlight) = info
                    val a = fraction(start); val b = fraction(stop)
                    if (b > a) Box(Modifier.offset(x = width * a).width(width * (b - a)).fillMaxHeight().padding(horizontal = .5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(if (highlight) colors.actual else colors.actualSurface)
                        .border(1.dp, colors.actualBorder, RoundedCornerShape(3.dp))) {
                        Text(name, Modifier.padding(horizontal = 4.dp).align(Alignment.CenterStart), fontSize = 10.sp, maxLines = 1,
                            overflow = TextOverflow.Clip, color = if (highlight) colors.onPrimary else colors.on)
                    }
                }
                Box(Modifier.offset(x = width * fraction(now)).width(1.5.dp).fillMaxHeight().background(colors.now))
            }
        }
    }
    val before = visible.map { Triple(parseActivityInstant(it.startAt), end(it), it.identityText() to false) }
    val after = visible.mapNotNull { r -> parseActivityInstant(r.startAt).takeIf { it < at }?.let { Triple(it, minOf(end(r), at), r.identityText() to false) } } +
        Triple(at, now, next to true)
    Column(Modifier.padding(vertical = 4.dp).semantics(mergeDescendants = true) { contentDescription = "Recorded time from ${clockLabel(at, zone)} is replaced by $next" },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Strip("Now", before)
        Strip("After", after)
        Row(Modifier.padding(start = 44.dp)) {
            Text(clockLabel(from, zone), style = type.monoSmall, color = colors.onVariant, modifier = Modifier.weight(1f))
            Text(clockLabel(now, zone), style = type.monoSmall, color = colors.onVariant)
        }
    }
}
