package com.timebox.android.ui.battleplan.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.timebox.android.data.TaskType
import com.timebox.android.ui.day.TaskTypePicker
import com.timebox.android.ui.theme.TimeboxTheme
import kotlinx.coroutines.delay

/** THROWAWAY: native A/B/C placement study for #168. No repository or network access. */
class TaskTypeRecommendationPrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val initialVariant = when (intent.data?.getQueryParameter("variant") ?: intent.getStringExtra("variant")) {
            "B" -> 1
            "C" -> 2
            else -> 0
        }
        setContent { TimeboxTheme(darkTheme = false) { RecommendationLab(initialVariant) } }
    }
}

private val variantNames = listOf("In the name editor", "Back in the details", "Name & type together")
private val surfaces = listOf("Battle Plan Task", "Planned Block", "Actual Block", "Recurring Task Series", "Session Task")
private val outcomes = listOf("Match · 0.93", "Low confidence · 0.72", "No suitable match", "Jev unavailable", "Over 254 categories")
private val initialTypes = listOf(TaskType(1, "learning/music", 12), TaskType(2, "health/exercise", 10), TaskType(3, "work/writing", 8), TaskType(4, "home/errands", 6), TaskType(5, "unspecified", 0))

@Composable
private fun RecommendationLab(initialVariant: Int) {
    var variant by rememberSaveable { mutableIntStateOf(initialVariant) }
    var generation by remember { mutableIntStateOf(0) }
    key(variant, generation) {
        NativeVariant(variant, { variant = (it + 3) % 3 }, { generation++ })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NativeVariant(variant: Int, switchVariant: (Int) -> Unit, reset: () -> Unit) {
    val colors = TimeboxTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    var surface by rememberSaveable { mutableIntStateOf(0) }
    var outcome by rememberSaveable { mutableIntStateOf(0) }
    var linked by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("Practise piano") }
    var draft by rememberSaveable { mutableStateOf(name) }
    var typeId by rememberSaveable { mutableStateOf<Int?>(null) }
    var draftTypeId by rememberSaveable { mutableStateOf<Int?>(null) }
    var explicit by rememberSaveable { mutableStateOf(false) }
    var draftExplicit by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(true) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var controls by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var changed by rememberSaveable { mutableStateOf(false) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var resultName by remember { mutableStateOf<String?>(null) }
    var resultOutcome by remember { mutableIntStateOf(-1) }
    var resultType by remember { mutableStateOf<TaskType?>(null) }
    var types by remember { mutableStateOf(initialTypes) }
    var event by rememberSaveable { mutableStateOf("Opened unclassified work. No request.") }
    val currentName = if (editing) draft else name
    val selectedId = if (editing) draftTypeId else typeId
    val manuallyChosen = if (editing) draftExplicit else explicit
    val locked = surface == 2 && linked
    val unclassified = selectedId == null || selectedId == 5
    val eligible = changed && currentName.isNotBlank() && unclassified && !manuallyChosen && !dismissed && !locked
    val fresh = resultName == currentName && resultOutcome == outcome
    val recommendation = resultType.takeIf { eligible && fresh && outcome == 0 }
    val stateLabel = when {
        locked -> "Linked classification — suppressed"
        manuallyChosen -> "Explicit choice protected"
        dismissed -> "Dismissed until name changes"
        !eligible -> "No request"
        !fresh -> "Waiting 500 ms"
        recommendation != null -> "Suggested: ${recommendation.name} · 0.93"
        else -> "No recommendation: ${outcomes[outcome]}"
    }
    LaunchedEffect(currentName, eligible, outcome) {
        if (!eligible) return@LaunchedEffect
        delay(500)
        val lower = currentName.lowercase()
        resultType = initialTypes[when {
            Regex("run|gym|walk|exercise").containsMatchIn(lower) -> 1
            Regex("piano|guitar|music").containsMatchIn(lower) -> 0
            Regex("shop|grocer|errand").containsMatchIn(lower) -> 3
            else -> 2
        }]
        resultName = currentName
        resultOutcome = outcome
        event = if (outcome == 0) "Simulated result ready; nothing assigned." else "Recommendation omitted. Manual selection available."
    }
    fun openName() {
        draft = name; draftTypeId = typeId; draftExplicit = explicit
        editing = true; picker = false; event = "Editing name; changes are a local draft."
    }
    fun discard() {
        keyboard?.hide(); editing = false; picker = false; changed = false
        resultName = null; resultType = null; dismissed = false; event = "Name edit and staged recommendation discarded."
    }
    fun save() {
        keyboard?.hide(); name = draft; typeId = draftTypeId; explicit = draftExplicit
        editing = false; picker = false; event = "Saved name and any accepted classification in this fixture."
    }
    fun choose(id: Int?, accepted: Boolean = false) {
        if (editing) { draftTypeId = id; draftExplicit = true } else { typeId = id; explicit = true }
        picker = false; query = ""; keyboard?.hide()
        event = if (accepted) "Recommendation accepted${if (editing) "; save to keep it" else ""}." else "Manual classification protected from later results."
    }
    fun cycleSurface() {
        surface = (surface + 1) % surfaces.size; name = "Practise piano"; draft = name
        typeId = null; draftTypeId = null; explicit = false; draftExplicit = false
        editing = false; picker = false; changed = false; dismissed = false; resultName = null; linked = false
        event = "Opened ${surfaces[surface]}. No request."
    }
    val switcher: @Composable () -> Unit = {
        Surface(color = colors.inverseSurface, contentColor = colors.bg, shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { keyboard?.hide(); switchVariant(variant - 1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous variant") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PROTOTYPE ${('A'.code + variant).toChar()} / C", fontSize = 9.sp)
                    Text(variantNames[variant], fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = { keyboard?.hide(); switchVariant(variant + 1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Next variant") }
                IconButton(onClick = { keyboard?.hide(); controls = true }) { Icon(Icons.Outlined.Tune, "Prototype controls") }
            }
        }
    }
    val suggestion: @Composable () -> Unit = {
        recommendation?.let { proposed ->
            Surface(color = colors.low, border = BorderStroke(1.dp, colors.outlineVariant), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable { choose(proposed.id, true) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = colors.project, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) { Text("Suggested Task Type", fontSize = 11.sp, color = colors.onVariant); Text(proposed.name, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                        Text("Use", color = colors.project, fontSize = 13.sp)
                    }
                    IconButton(onClick = { dismissed = true; event = "Dismissed until name changes." }) { Icon(Icons.Outlined.Close, "Dismiss recommendation", Modifier.size(18.dp)) }
                }
            }
        }
    }
    val typeButton: @Composable () -> Unit = {
        OutlinedButton(onClick = { query = ""; picker = true }, enabled = !locked) {
            Icon(Icons.AutoMirrored.Outlined.Label, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(if (locked) "learning/music" else types.find { it.id == selectedId }?.name ?: if (surface in 1..2) "unspecified" else "Unset")
        }
    }
    Surface(Modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
            Text("TIMEBOX", fontSize = 11.sp, color = colors.onVariant)
            Text(if (surface in 1..2) "Day" else "Battle Plan", fontSize = 30.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 16.dp))
            Text("All tasks       Projects       Recurring", fontSize = 14.sp)
            HorizontalDivider(Modifier.padding(vertical = 20.dp), color = colors.hairline)
            Text("READY TO PLAN", fontSize = 11.sp, color = colors.onVariant)
            listOf("Review the launch brief", name, "Buy groceries").forEach { title ->
                Surface(onClick = { showDetails = true }, color = colors.card, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.RadioButtonUnchecked, null); Spacer(Modifier.width(12.dp)); Text(title) }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Native Android · simulated recommendations", color = colors.onVariant, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
            switcher()
        }
    }
    if (showDetails) {
        ModalBottomSheet(onDismissRequest = { if (editing) discard() else showDetails = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.sheet.copy(alpha = 1f)) {
            BackHandler(editing || picker) { if (picker) picker = false else discard() }
            Column(Modifier.fillMaxWidth().imePadding().heightIn(max = 710.dp).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (picker) "Task Type" else if (editing) if (variant == 2) "Name & Task Type" else "Name" else surfaces[surface], fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (picker) picker = false else if (editing) discard() else showDetails = false }) { Icon(Icons.Outlined.Close, if (editing) "Discard name edit" else "Close details") }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (picker) {
                        TaskTypePicker(types, query, { query = it }, selectedId, { choose(it.id) }, { path ->
                            val created = TaskType(types.maxOf { it.id } + 1, path, 0)
                            types = types + created; choose(created.id)
                        }, allowUnset = surface !in 1..2, onUnset = { choose(null) })
                    } else if (editing) {
                        val focus = remember { FocusRequester() }
                        OutlinedTextField(draft, {
                            draft = it; changed = true; dismissed = false; resultName = null
                        }, label = { Text(if (surface in 1..2) "Block Name" else "Name") }, modifier = Modifier.fillMaxWidth().focusRequester(focus), minLines = 1, maxLines = 3)
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        if (variant == 0) { suggestion(); if (!unclassified) Text("Task Type: ${types.find { it.id == selectedId }?.name}", color = colors.onVariant, fontSize = 13.sp) }
                        if (variant == 2) { Text("Task Type", color = colors.onVariant, fontSize = 12.sp); typeButton(); suggestion() }
                        Text(if (variant == 1) "Save your name to return to the details." else "Save when you’re ready. A Task Type is optional.", color = colors.onVariant, fontSize = 12.sp)
                    } else {
                        Row(Modifier.fillMaxWidth().clickable { openName() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (surface !in 1..2) { Icon(Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(28.dp)); Spacer(Modifier.width(12.dp)) }
                            Text(name.ifBlank { "Unnamed block" }, fontSize = 25.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.Edit, "Edit name", tint = colors.onVariant, modifier = Modifier.size(18.dp))
                        }
                        Text(if (surface in 1..2) "No supporting note" else "No description", color = colors.onVariant, fontSize = 14.sp)
                        HorizontalDivider(color = colors.hairline)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (surface in 1..2) { AssistChip({}, { Text("10:00 – 10:30") }) }
                            else { AssistChip({}, { Text(if (surface == 3) "Every weekday" else "Deadline") }); AssistChip({}, { Text("Importance") }); AssistChip({}, { Text("Reminder") }) }
                            typeButton()
                        }
                        if (variant == 1) suggestion()
                        if (locked) Text("Task Type follows its Planned Block.", color = colors.onVariant, fontSize = 12.sp)
                        if (surface !in 1..2) { Text("Subtasks", fontWeight = FontWeight.Medium); Text("No subtasks yet", color = colors.onVariant, fontSize = 13.sp) }
                        TextButton(onClick = { openName() }) { Text(if (variant == 2) "Edit name & type" else "Edit name") }
                    }
                }
                if (editing && !picker) Button(onClick = { save() }, enabled = draft.isNotBlank() || surface in 1..2, modifier = Modifier.fillMaxWidth()) { Text(if (variant == 2) "Save changes" else "Save name") }
                HorizontalDivider(color = colors.hairline)
                Text("LAB · $stateLabel", fontSize = 10.sp, color = colors.onVariant, maxLines = 2)
                switcher()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (controls) AlertDialog(onDismissRequest = { controls = false }, title = { Text("Prototype controls") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fixtures only · no Jev or Phoenix requests", fontSize = 12.sp)
            TextButton(onClick = { cycleSurface() }) { Text("Surface: ${surfaces[surface]} ›") }
            TextButton(onClick = { outcome = (outcome + 1) % outcomes.size; resultName = null }) { Text("Response: ${outcomes[outcome]} ›") }
            if (surface == 2) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(linked, { linked = it }); Text("Linked Actual Block") }
            HorizontalDivider()
            Text("Name: $currentName\nType: ${types.find { it.id == selectedId }?.name ?: "unclassified"}\n$stateLabel", fontSize = 12.sp)
            Text(event, fontSize = 12.sp, color = colors.onVariant)
            Text("Try Practise guitar, Morning run, or Buy groceries. Pause for 500 ms. Use the arrows to compare A, B, and C.", fontSize = 12.sp)
            TextButton(onClick = { controls = false; reset() }) { Text("Reset this variant") }
        }
    }, confirmButton = { TextButton(onClick = { controls = false }) { Text("Done") } })
}
