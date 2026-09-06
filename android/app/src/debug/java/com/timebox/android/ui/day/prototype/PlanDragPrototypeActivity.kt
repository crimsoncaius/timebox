package com.timebox.android.ui.day.prototype

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timebox.android.ui.theme.TimeboxTheme
import kotlin.math.roundToInt

/** THROWAWAY: Does one block-sized object make Plan dragging clearer?
 * Native counterpart of the UI prototype route: timebox://prototype/plan-drag?variant=A.
 * Debug source set only; fixture data, no API writes. Variants reset independently.
 */
class PlanDragPrototypeActivity : ComponentActivity() {
    private var variant by mutableIntStateOf(0)
    private fun readVariant() { variant = listOf("A", "B", "C").indexOf(intent.data?.getQueryParameter("variant")).coerceAtLeast(0) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readVariant()
        setContent { TimeboxTheme(darkTheme = true) { key(variant) { PlanDragPrototype(variant, ::choose) } } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readVariant() }
    private fun choose(next: Int) {
        variant = (next + 3) % 3
        setIntent(Intent(intent).setData(Uri.parse("timebox://prototype/plan-drag?variant=${"ABC"[variant]}")))
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when(keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { choose(variant - 1); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { choose(variant + 1); true }
        else -> super.onKeyUp(keyCode, event)
    }
}

private data class SampleBlock(val id: Int, val title: String, val start: Int, val minutes: Int)
private data class HeldBlock(val block: SampleBlock, val point: Offset, val grab: Float, val fromRail: Boolean)
private val blue = Color(0xFF8AB4F8)
private val base = Color(0xFF10141C)
private val samples = listOf(SampleBlock(1, "Align the mobile version", 60, 30), SampleBlock(2, "Increase granularity to 5 minutes", 120, 30), SampleBlock(3, "Morning Routine · Task O", 300, 30))
private fun clock(minutes: Int) = "${if (minutes / 60 == 0) 12 else minutes / 60}:${(minutes % 60).toString().padStart(2, '0')} AM"

@Composable
private fun PlanDragPrototype(variant: Int, choose: (Int) -> Unit) {
    var blocks by remember { mutableStateOf(samples) }
    var held by remember { mutableStateOf<HeldBlock?>(null) }
    var railMinutes by remember { mutableIntStateOf(30) }
    var notice by remember { mutableStateOf("Hold a task, then drag it into Planned.") }
    val names = listOf("Snap into place", "Carry the whole block", "Focus on placement")
    val descriptions = listOf("One card locks to the lane and time grid.", "One full-sized card follows your finger; an outline marks its destination.", "The task rail steps aside while you place the block.")
    Column(Modifier.fillMaxSize().background(Color(0xFF0C0C0B)).statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
        Text("DAY", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sun, September 6", fontSize = 25.sp, modifier = Modifier.weight(1f))
            Text("✓ Done", color = Color(0xFF11264A), modifier = Modifier.clip(RoundedCornerShape(30.dp)).background(blue).padding(14.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("✓ Today", color = blue)
            Text("Week     Month", color = Color.LightGray)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("MON\n31", "TUE\n1", "WED\n2", "THU\n3", "FRI\n4", "SAT\n5", "SUN\n6").forEachIndexed { i, label ->
                Text(label, fontSize = 12.sp, color = if (i == 6) blue else Color.Gray, modifier = Modifier.padding(5.dp))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(44.dp))
            Text("PLANNED", color = blue, fontSize = 10.sp, modifier = Modifier.weight(.62f))
            Text("TASKS TO PLAN", color = blue, fontSize = 10.sp, modifier = Modifier.weight(.38f))
        }
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            val totalW = maxWidth.value
            val totalH = maxHeight.value
            val left = 44f
            val railX = left + (totalW - left) * .62f
            val normalWidth = railX - left - 8f
            val minuteHeight = totalH / 390f
            val dragging = held
            val focus = variant == 2 && dragging != null
            val laneWidth = if (focus) totalW - left else normalWidth
            val candidate = dragging?.let { ((it.point.y - it.grab) / minuteHeight / 5).roundToInt() * 5 }?.coerceIn(0, 390 - (dragging?.block?.minutes ?: 30)) ?: 0
            val inLane = dragging != null && dragging.point.x >= left && dragging.point.x < (if (focus) totalW else railX) && dragging.point.y in 0f..totalH
            val overlaps = dragging != null && blocks.any { it.id != dragging.block.id && candidate < it.start + it.minutes && candidate + dragging.block.minutes > it.start }
            val valid = inLane && !overlaps
            val accent = if (inLane && overlaps) Color(0xFFFFA6A0) else blue
            Box(Modifier.fillMaxSize().pointerInput(variant, totalW, totalH, railMinutes) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { px ->
                        val p = with(density) { Offset(px.x.toDp().value, px.y.toDp().value) }
                        val block = blocks.lastOrNull { p.x in left..railX && p.y in (it.start * minuteHeight)..((it.start + it.minutes) * minuteHeight) }
                        if (block != null) held = HeldBlock(block, p, p.y - block.start * minuteHeight, false)
                        else if (p.x >= railX && p.y < 84 && blocks.none { it.id == 4 }) held = HeldBlock(SampleBlock(4, "Refine mobile design", 0, railMinutes), p, railMinutes * minuteHeight / 2, true)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        held = held?.let { it.copy(point = it.point + with(density) { Offset(amount.x.toDp().value, amount.y.toDp().value) }) }
                    },
                    onDragEnd = {
                        held?.let { h ->
                            val t = (((h.point.y - h.grab) / minuteHeight / 5).roundToInt() * 5).coerceIn(0, 390 - h.block.minutes)
                            val within = h.point.x >= left && h.point.x < (if(variant == 2) totalW else railX) && h.point.y in 0f..totalH
                            val collision = blocks.any { it.id != h.block.id && t < it.start + it.minutes && t + h.block.minutes > it.start }
                            if (within && !collision) { blocks = blocks.filter { it.id != h.block.id } + h.block.copy(start = t); notice = "Placed at ${clock(t)} · ${h.block.minutes} min" }
                            else notice = "Cancelled · ${if(collision && within) "that time is occupied" else "released outside Planned"}"
                        }
                        held = null
                    },
                    onDragCancel = { held = null; notice = "Cancelled · original position restored" },
                )
            }) {
                Box(Modifier.offset(x = left.dp).width(laneWidth.dp).fillMaxHeight().background(base))
                if (!focus) Box(Modifier.offset(x = railX.dp).width((totalW - railX).dp).fillMaxHeight().background(Color(0xFF1D1A17)))
                for (hour in 0..6) {
                    val y = hour * 60 * minuteHeight
                    Text(if(hour == 0) "12 AM" else "$hour AM", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.offset(y = y.dp))
                    Box(Modifier.offset(x = left.dp, y = y.dp).width(laneWidth.dp).height(1.dp).background(Color.White.copy(alpha = .09f)))
                }
                blocks.forEach { b ->
                    if (dragging?.block?.id != b.id) PrototypeBlock(b.title, false, blue, Modifier.offset(x = left.dp, y = (b.start * minuteHeight).dp).width(laneWidth.dp).height((b.minutes * minuteHeight).dp))
                    else Box(Modifier.offset(x = left.dp, y = (b.start * minuteHeight).dp).width(laneWidth.dp).height((b.minutes * minuteHeight).dp).background(blue.copy(alpha = .05f)))
                }
                if (!focus && blocks.none { it.id == 4 }) {
                    if (dragging?.fromRail == true) Text("Placing…", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.offset(x = (railX + 8).dp, y = 10.dp))
                    else Column(Modifier.offset(x = (railX + 4).dp, y = 4.dp).width((totalW - railX - 8).dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF090909)).padding(8.dp)) {
                        Text("Refine mobile design", fontSize = 14.sp)
                        Text("$railMinutes min", color = Color.Gray, fontSize = 11.sp)
                    }
                }
                if (dragging != null) {
                    val height = dragging.block.minutes * minuteHeight
                    val x = if (variant == 1 || !inLane) (dragging.point.x - normalWidth / 2).coerceIn(left, totalW - normalWidth) else left
                    val y = if (variant == 1 || !inLane) (dragging.point.y - dragging.grab).coerceIn(0f, totalH - height) else candidate * minuteHeight
                    if (variant == 1 && inLane) Box(Modifier.offset(x = left.dp, y = (candidate * minuteHeight).dp).width(normalWidth.dp).height(height.dp).padding(vertical = 1.dp).border(1.dp, accent.copy(alpha = .65f), RoundedCornerShape(6.dp)))
                    PrototypeBlock(dragging.block.title, true, accent, Modifier.offset(x = x.dp, y = y.dp).width((if (focus) laneWidth else normalWidth).dp).height(height.dp))
                    Text(if (!inLane) "Move into Planned" else if (overlaps) "Time occupied · release to cancel" else "${clock(candidate)} – ${clock(candidate + dragging.block.minutes)}", color = accent, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xFF080808)).padding(6.dp))
                }
            }
        }
        Text(descriptions[variant], color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        Text(if(held == null) notice else "Holding: ${held!!.block.title} · ${held!!.block.minutes} min", color = blue, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { blocks = samples; held = null; notice = "Reset · hold a task or existing block." }) { Text("Reset") }
            TextButton(onClick = { railMinutes = if(railMinutes == 30) 60 else 30; held = null }) { Text("New: $railMinutes min") }
            TextButton(onClick = { held = null; notice = "Cancelled · original position restored" }) { Text("Cancel") }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(30.dp)).background(Color(0xFF29354B)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { choose(variant - 1) }) { Text("←", color = blue) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PROTOTYPE · ${"ABC"[variant]}", fontSize = 9.sp, color = blue)
                Text(names[variant], fontSize = 12.sp)
            }
            TextButton(onClick = { choose(variant + 1) }) { Text("→", color = blue) }
        }
    }
}

@Composable
private fun PrototypeBlock(title: String, active: Boolean, accent: Color, modifier: Modifier) {
    Box(modifier.padding(vertical = 1.dp).then(if(active) Modifier.shadow(8.dp, RoundedCornerShape(6.dp)) else Modifier).clip(RoundedCornerShape(6.dp)).background(if(active) Color(0xFF263B59) else Color(0xFF1B222C)).border(if(active) 2.dp else .5.dp, if(active) accent else Color(0xFF344457), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp), contentAlignment = Alignment.CenterStart) {
        Text(title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
