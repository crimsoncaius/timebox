package com.timebox.android.ui.day.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.timebox.android.data.Day
import com.timebox.android.data.Lane
import com.timebox.android.data.TimeBlock
import com.timebox.android.ui.day.DayTimeline
import com.timebox.android.ui.theme.TimeboxTheme
import java.time.LocalDate

// THROWAWAY: native round 1, isolated sample data; no API or persistence.
class ActivityTrackingPrototypeActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  enableEdgeToEdge()
  setContent { TimeboxTheme(darkTheme = false) { TrackingRound() } }
 }
}
private data class Entry(val type: String, val name: String, val start: Int, val end: Int? = null)
private fun clock(n: Int) = "%02d:%02d".format(n / 60, n % 60)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingRound() {
 var focus by remember { mutableStateOf(false) }
 var now by remember { mutableIntStateOf(660) }
 var entries by remember { mutableStateOf(listOf<Entry>()) }
 var editing by remember { mutableStateOf(false) }
 BackHandler(enabled = focus && !editing) { focus = false }
 var type by remember { mutableStateOf("") }
 var name by remember { mutableStateOf("") }
 val active = entries.lastOrNull { it.end == null }
 fun start(nextType: String, nextName: String) {
  entries = entries.map { if(it.end == null) it.copy(end = now) else it } + Entry(nextType, nextName, now)
  editing = false
 }
 val date = LocalDate.of(2026, 9, 10)
 fun block(id: Int, lane: Lane, title: String, category: String, from: Int, to: Int) = TimeBlock(id, lane, 1, category, null, null, null, null, startMinute = from, endMinute = to, name = title)
 val plans = listOf(block(1, Lane.Planned, "Writing a proposal", "Work", 600, 720), block(2, Lane.Planned, "Lunch", "Break", 720, 780))
 val actual = entries.mapIndexed { i, e -> block(100+i, Lane.Actual, e.name.ifBlank { e.type }, e.type, e.start, e.end ?: now) }
 val day = Day(date, 9, 15, false, plans + actual, timezone = "Asia/Singapore", today = date, serverNowMinute = now)
 Surface(modifier = Modifier.fillMaxSize()) {
 Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp), verticalAlignment=Alignment.CenterVertically) {
   Text("PROTOTYPE 2 · ${clock(now)}", style=MaterialTheme.typography.labelSmall, modifier=Modifier.weight(1f))
   TextButton(onClick={now+=5}) { Text("+5 min") }
   TextButton(onClick={now=660; entries=emptyList(); editing=false; focus=false}) { Text("Reset") }
  }
  if (focus && active != null) {
   Row(Modifier.fillMaxWidth().padding(horizontal=20.dp), verticalAlignment=Alignment.CenterVertically) {
    Text("Focus", style=MaterialTheme.typography.labelMedium, modifier=Modifier.weight(1f))
    TextButton(onClick={focus=false}) { Text("Exit Focus") }
   }
   Column(Modifier.weight(1f).fillMaxWidth().padding(28.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
    Text("Recording since ${clock(active.start)}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    Text(active.name.ifBlank { active.type }, style=MaterialTheme.typography.headlineLarge, textAlign=TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Text("${now-active.start} min", style=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
    TextButton(onClick={type="";name="";editing=true}) { Text("Switch activity") }
   }
   Text("Tracking continues when you exit Focus.", modifier=Modifier.fillMaxWidth().padding(20.dp), textAlign=TextAlign.Center, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
  } else {
  Column(Modifier.padding(horizontal=20.dp)) {
   Text("Day", style=MaterialTheme.typography.headlineMedium)
   Text("Thursday, 10 September", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
   Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
    if(active == null) {
     Spacer(Modifier.weight(1f))
     TextButton(onClick={ val plan=plans.find { now >= it.startMinute && now < it.endMinute }; start(plan?.taskTypeName ?: "unspecified", plan?.name ?: "") }) { Text("Start tracking") }
    } else {
     Column(Modifier.weight(1f)) {
      Text(active.name.ifBlank { active.type }, maxLines=1, overflow=TextOverflow.Ellipsis, style=MaterialTheme.typography.bodySmall)
      Text("Recording · ${now-active.start} min", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
     TextButton(onClick={type="";name="";editing=true}) { Text("Switch") }
     TextButton(onClick={entries=entries.map { if(it.end==null) it.copy(end=now) else it }}) { Text("Stop") }
    }
    TextButton(onClick={
     if(active == null) { val plan=plans.find { now >= it.startMinute && now < it.endMinute }; start(plan?.taskTypeName ?: "unspecified", plan?.name ?: "") }
     focus=true
    }) { Text("Focus") }
   }
  }
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
   DayTimeline(day=day, selectedBlockId=null, draft=null, onTapSlot={_,_->}, onSelectBlock={}, onCommitMove={_,_,_->}, blockGesturesEnabled=false)
  }
  Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement=Arrangement.SpaceAround) {
   listOf("Day", "Battle Plan", "Chronicle", "Settings").forEach { Text(it, style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
  }
 }
 }
 }
 if(editing) ModalBottomSheet(onDismissRequest={editing=false}) {
  Column(Modifier.fillMaxWidth().imePadding().padding(24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text("Switch activity", style=MaterialTheme.typography.titleLarge)
   Text("Task type", style=MaterialTheme.typography.labelMedium)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    listOf("Work", "Break", "Personal").forEach { choice -> FilterChip(selected=type==choice, onClick={type=choice}, label={Text(choice)}) }
   }
   OutlinedTextField(value=name, onValueChange={name=it}, label={Text("Activity name (optional)")}, singleLine=true, modifier=Modifier.fillMaxWidth())
   Button(enabled=type.isNotEmpty(), onClick={start(type,name.trim())}, modifier=Modifier.fillMaxWidth()) { Text("Switch now") }
  }
 }
}
