package com.timebox.android.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timebox.android.data.DaySummary
import com.timebox.android.data.TimeboxRepository
import com.timebox.android.data.apiError
import com.timebox.android.ui.durationShort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/** One bar pair in the review, rolled up to the root of the task type path. */
data class ReviewRow(
    val name: String,
    val plannedMinutes: Int,
    val actualMinutes: Int,
) {
    val driftMinutes: Int get() = actualMinutes - plannedMinutes
}

data class ReviewUiState(
    val date: LocalDate = LocalDate.now(),
    val plannedMinutes: Int = 0,
    val actualMinutes: Int = 0,
    val rows: List<ReviewRow> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    /** Longest bar in the chart, so both lanes share one scale. */
    val maxMinutes: Int
        get() = maxOf(1, rows.maxOfOrNull { maxOf(it.plannedMinutes, it.actualMinutes) } ?: 1)
}

class ReviewViewModel(private val repository: TimeboxRepository) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    fun load(date: LocalDate) {
        _state.update { it.copy(date = date, loading = true, error = null) }
        viewModelScope.launch {
            repository.getDaySummary(date).fold(
                onSuccess = { summary ->
                    _state.update {
                        it.copy(
                            date = summary.date,
                            plannedMinutes = summary.plannedMinutes,
                            actualMinutes = summary.actualMinutes,
                            rows = rollUpToRoots(summary),
                            loading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.apiError.message) }
                },
            )
        }
    }

    /**
     * The API reports each task type separately; the review groups by the first path
     * segment so `coding/ai` and `coding/review` read as one line.
     */
    private fun rollUpToRoots(summary: DaySummary): List<ReviewRow> =
        summary.rows
            .groupBy { it.taskTypeName.substringBefore('/') }
            .map { (root, rows) ->
                ReviewRow(
                    name = root,
                    plannedMinutes = rows.sumOf { it.plannedMinutes },
                    actualMinutes = rows.sumOf { it.actualMinutes },
                )
            }
            .sortedByDescending { it.plannedMinutes + it.actualMinutes }
}

/**
 * Describe the day's drift in plain sentences, built only from the numbers we have.
 */
fun driftCopy(state: ReviewUiState): String {
    if (state.rows.isEmpty()) {
        return "Nothing recorded for this day yet."
    }

    val sentences = mutableListOf<String>()

    val overrun = state.rows.filter { it.driftMinutes > 0 }.maxByOrNull { it.driftMinutes }
    if (overrun != null) {
        sentences += "${overrun.name} ran ${durationShort(overrun.driftMinutes)} past plan."
    }

    val shortfall = state.rows.filter { it.driftMinutes < 0 }.minByOrNull { it.driftMinutes }
    if (shortfall != null) {
        val missed = abs(shortfall.driftMinutes)
        sentences += if (shortfall.actualMinutes == 0) {
            "${shortfall.name} did not happen; ${durationShort(missed)} went elsewhere."
        } else {
            "${shortfall.name} came up ${durationShort(missed)} short."
        }
    }

    val unplanned = state.rows.filter { it.plannedMinutes == 0 && it.actualMinutes > 0 }
    if (unplanned.isNotEmpty()) {
        val names = unplanned.joinToString(", ") { it.name }
        sentences += "Unplanned time went to $names."
    }

    sentences += "Overall you recorded ${durationShort(state.actualMinutes)} against " +
        "${durationShort(state.plannedMinutes)} planned."

    return sentences.joinToString(" ")
}
