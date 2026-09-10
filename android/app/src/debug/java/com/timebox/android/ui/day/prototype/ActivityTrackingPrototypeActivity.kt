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
 var offline by remember { mutableStateOf(false) }
 var keepAwake by remember { mutableStateOf(true) }
 var lifecycleNotice by remember { mutableStateOf("") }
 var remoteNotice by remember { mutableStateOf(false) }
 val context = androidx.compose.ui.platform.LocalContext.current
 var stopEditing by remember { mutableStateOf(false) }
 var changeAt by remember { mutableIntStateOf(750) }
 var checkInOpen by remember { mutableStateOf(true) }
 var pending by remember { mutableStateOf(false) }
 var focus by remember { mutableStateOf(true) }
 var now by remember { mutableIntStateOf(750) }
 var entries by remember { mutableStateOf(listOf(Entry("Work", "Writing a proposal", 600,720), Entry("Break", "Lunch", 720))) }
 var editing by remember { mutableStateOf(false) }
 BackHandler(enabled = focus && !editing && !stopEditing) { focus = false }
 var type by remember { mutableStateOf("") }
 var name by remember { mutableStateOf("") }
 val active = entries.lastOrNull { it.end == null }
 val needsDescription = active?.type == "unspecified" && active.name.isBlank()
 fun start(nextType: String, nextName: String, at: Int = now) {
  if(at > now || (active != null && at < active.start)) return
  entries = entries.map { if(it.end == null) it.copy(end = at) else it } + Entry(nextType, nextName, at)
  editing = false
  pending = false
 }
 val date = LocalDate.of(2026, 9, 10)
 fun block(id: Int, lane: Lane, title: String, category: String, from: Int, to: Int) = TimeBlock(id, lane, 1, category, null, null, null, null, startMinute = from, endMinute = to, name = title)
 val plans = listOf(block(1, Lane.Planned, "Writing a proposal", "Work", 600, 720), block(2, Lane.Planned, "Lunch", "Break", 720, 780))
 val suggested = plans.find { active != null && it.startMinute > active.start && now >= it.startMinute && now < it.endMinute }
 val suggestion: @Composable () -> Unit = {
  if(suggested != null) Surface(color=MaterialTheme.colorScheme.surfaceContainerLow, shape=MaterialTheme.shapes.medium, modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)) {
   Row(Modifier.padding(start=12.dp,end=4.dp), verticalAlignment=Alignment.CenterVertically) {
    Text("${suggested.name} is planned now", modifier=Modifier.weight(1f), style=MaterialTheme.typography.bodySmall, fontWeight=androidx.compose.ui.text.font.FontWeight.Medium)
    TextButton(onClick={start(suggested.taskTypeName,suggested.name ?: "")}) { Text("Switch") }
   }
  }
 }
 val checkIn: @Composable () -> Unit = {
  if(pending && active != null) TextButton(onClick={checkInOpen=true}) { Text("Check-in waiting") }
 }
 val recovery: @Composable () -> Unit = {
  if(remoteNotice) Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)) {
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Text("Updated from another device",style=MaterialTheme.typography.titleSmall)
    Text("A newer change set Reading from 12:10. Earlier recorded time was kept.",style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={remoteNotice=false}) { Text("Got it") }
   }
  }
 }
 val actual = entries.mapIndexed { i, e -> block(100+i, Lane.Actual, e.name.ifBlank { e.type }, e.type, e.start, e.end ?: now) }
 val day = Day(date, 9, 15, false, plans + actual, timezone = "Asia/Singapore", today = date, serverNowMinute = now)
 Surface(modifier = Modifier.fillMaxSize()) {
 Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp), verticalAlignment=Alignment.CenterVertically) {
   Text("PROTOTYPE 8 · ${clock(now)}", style=MaterialTheme.typography.labelSmall, modifier=Modifier.weight(1f))
   TextButton(onClick={now+=5}) { Text("+5 min") }
   TextButton(onClick={now=750; offline=false;remoteNotice=false;keepAwake=true;lifecycleNotice=""; stopEditing=false; pending=false; checkInOpen=true; entries=listOf(Entry("Work", "Writing a proposal", 600,720),Entry("Break","Lunch",720)); editing=false; focus=true; type=""; name=""}) { Text("Reset") }
  }
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
   Text("SIMULATE",style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f))
   TextButton(onClick={now+=15;lifecycleNotice="Back in Focus. Your activity continued while away."}) { Text("Return +15m") }
   TextButton(enabled=active!=null,onClick={entries=entries.map { if(it.end==null) it.copy(end=now) else it };focus=false;pending=false;editing=false;stopEditing=false;lifecycleNotice="Tracking stopped on another device. Focus ended."}) { Text("Remote stop") }
  }
  if(lifecycleNotice.isNotEmpty()) Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp)) {
   Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
    Text(lifecycleNotice,style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
    TextButton(onClick={lifecycleNotice=""}) { Text("Got it") }
   }
  }
  if (focus && active != null) {
   Row(Modifier.fillMaxWidth().padding(horizontal=20.dp), verticalAlignment=Alignment.CenterVertically) {
    Text("Focus", style=MaterialTheme.typography.labelMedium, modifier=Modifier.weight(1f))
    TextButton(onClick={focus=false}) { Text("Exit Focus") }
   }
   Column(Modifier.weight(1f).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
    Text(if(offline) "Offline · saved on this device" else "Synced",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Recording since ${clock(active.start)}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    Text(if(needsDescription) "What are you doing right now?" else active.name.ifBlank { active.type }, style=MaterialTheme.typography.headlineLarge, textAlign=TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Text("${now-active.start} min", style=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
    if(needsDescription) {
     Text("Task type", style=MaterialTheme.typography.labelMedium, modifier=Modifier.fillMaxWidth())
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      listOf("Work", "Break", "Personal").forEach { choice -> FilterChip(selected=type==choice, onClick={type=choice}, label={Text(choice)}) }
     }
     Spacer(Modifier.height(12.dp))
     Text("Apply to the time already recorded, or leave it unspecified and start now.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
     Spacer(Modifier.height(12.dp))
     Button(enabled=type.isNotEmpty(), onClick={entries=entries.map { if(it.end==null) it.copy(type=type,name="") else it };type="";name=""}, modifier=Modifier.fillMaxWidth()) { Text("Apply from ${clock(active.start)}") }
     TextButton(enabled=type.isNotEmpty(), onClick={start(type,"");type="";name=""}) { Text("Start now") }
    } else TextButton(onClick={type="";name="";changeAt=now;editing=true}) { Text("Switch activity") }
    suggestion()
    checkIn()
    recovery()
   }
   Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
     Text("Keep screen awake",style=MaterialTheme.typography.bodySmall)
     Text(if(keepAwake) "Requested while visible · simulated" else "Off",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked=keepAwake,onCheckedChange={keepAwake=it})
   }
   Text("Tracking continues when you exit Focus.", modifier=Modifier.fillMaxWidth().padding(20.dp), textAlign=TextAlign.Center, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
  } else {
  Column(Modifier.padding(horizontal=20.dp)) {
   Text("Day", style=MaterialTheme.typography.headlineMedium)
   Text(if(offline) "Offline · saved on this device" else "Synced",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
     TextButton(onClick={type="";name="";changeAt=now;editing=true}) { Text("Switch") }
     TextButton(onClick={changeAt=now;stopEditing=true}) { Text("Stop") }
    }
    TextButton(onClick={
     if(active == null) { val plan=plans.find { now >= it.startMinute && now < it.endMinute }; start(plan?.taskTypeName ?: "unspecified", plan?.name ?: "") }
     focus=true
    }) { Text("Focus") }
   }
  }
  Column(Modifier.padding(horizontal=20.dp)) { suggestion(); checkIn(); recovery() }
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
   DayTimeline(day=day, selectedBlockId=null, draft=null, onTapSlot={_,_->}, onSelectBlock={}, onCommitMove={_,_,_->}, blockGesturesEnabled=false)
  }
  Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement=Arrangement.SpaceAround) {
   listOf("Day", "Battle Plan", "Chronicle", "Settings").forEach { Text(it, style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
  }
 }
 }
 }
 if(pending && active != null && checkInOpen && !editing) ModalBottomSheet(
  onDismissRequest={checkInOpen=false},
  sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
  shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=28.dp,topEnd=28.dp),
  containerColor=MaterialTheme.colorScheme.surface
 ) {
  Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(top=28.dp,bottom=32.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
   Text("Still working on this activity?", style=MaterialTheme.typography.headlineMedium, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
   Text(active.name.ifBlank { active.type }, style=MaterialTheme.typography.titleMedium)
   Text("We haven't detected device activity for a while. Your time is still being recorded.", style=MaterialTheme.typography.bodyLarge, color=MaterialTheme.colorScheme.onSurfaceVariant)
   Spacer(Modifier.height(8.dp))
   Button(onClick={pending=false;checkInOpen=false}, shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp), modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Still doing this") }
   OutlinedButton(onClick={checkInOpen=false;type="";name="";changeAt=now;editing=true}, shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp), modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Switch activity") }
  }
 }
 if((editing || stopEditing) && active != null) ModalBottomSheet(onDismissRequest={editing=false;stopEditing=false}, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
  val valid = changeAt in active.start..now
  Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text(if(stopEditing) "Finish tracking" else "Switch activity", style=MaterialTheme.typography.titleLarge)
   if(!stopEditing) {
    Text("Task type", style=MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     listOf("Work", "Break", "Personal").forEach { choice -> FilterChip(selected=type==choice, onClick={type=choice}, label={Text(choice)}) }
    }
    OutlinedTextField(value=name, onValueChange={name=it}, label={Text("Activity name (optional)")}, singleLine=true, modifier=Modifier.fillMaxWidth())
   }
   HorizontalDivider()
   Text(if(stopEditing) "When did you stop?" else "When did you switch?", style=MaterialTheme.typography.labelMedium)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick={android.app.TimePickerDialog(context, { _, h, m -> changeAt=h*60+m }, changeAt/60,changeAt%60,true).show()}) { Text(clock(changeAt)) }
    TextButton(onClick={changeAt=(now-15).coerceAtLeast(active.start)}) { Text("15 min ago") }
   }
   Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
     Text("After this change", style=MaterialTheme.typography.labelMedium)
     if(valid) {
      Row { Text(active.name.ifBlank { active.type },modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall); Text("${clock(active.start)}–${clock(changeAt)}",style=MaterialTheme.typography.bodySmall) }
      HorizontalDivider()
      Row { Text(if(stopEditing) "Untracked" else name.ifBlank { type.ifBlank { "Next activity" } },modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall); Text("${clock(changeAt)}–${if(stopEditing) clock(now) else "now"}",style=MaterialTheme.typography.bodySmall) }
     } else Text("Choose a time between ${clock(active.start)} and ${clock(now)}.",style=MaterialTheme.typography.bodySmall)
    }
   }
   Button(enabled=valid && (stopEditing || type.isNotEmpty()),onClick={
    if(stopEditing) { entries=entries.map { if(it.end==null) it.copy(end=changeAt) else it };pending=false;stopEditing=false }
    else start(type,name.trim(),changeAt)
   },modifier=Modifier.fillMaxWidth()) { Text("${if(stopEditing) "Stop" else "Switch"} at ${clock(changeAt)}") }
   TextButton(onClick={editing=false;stopEditing=false},modifier=Modifier.fillMaxWidth()) { Text("Cancel") }
  }
 }
}
