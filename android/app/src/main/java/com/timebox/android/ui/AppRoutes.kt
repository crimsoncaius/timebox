package com.timebox.android.ui

import java.time.LocalDate

/** Stable route names shared by in-app navigation and future notification intents. */
object AppRoutes {
    const val DateArg = "date"
    const val TaskIdArg = "taskId"
    const val BlockIdArg = "blockId"
    const val ProjectIdArg = "projectId"
    const val TemplateIdArg = "templateId"

    const val DayPattern = "day/{$DateArg}?blockId={$BlockIdArg}"
    const val Chronicle = "chronicle"
    const val BattlePlan = "battle-plan"
    const val TaskDetailPattern = "battle-plan/task/{$TaskIdArg}"
    const val ProjectNew = "battle-plan/project/new"
    const val ProjectDetailPattern = "battle-plan/project/{$ProjectIdArg}"
    const val Recurring = "battle-plan/recurring"
    const val RecurringNew = "battle-plan/recurring/new"
    const val RecurringDetailPattern = "battle-plan/recurring/{$TemplateIdArg}"
    const val RecurringEditPattern = "battle-plan/recurring/{$TemplateIdArg}/edit"
    const val Types = "types"
    const val Settings = "settings"
    const val ThemePreview = "settings/theme-preview"

    const val TaskDeepLinkPattern = "timebox://battle-plan/task/{$TaskIdArg}"

    fun day(date: LocalDate, blockId: Int? = null): String =
        "day/$date" + (blockId?.let { "?blockId=$it" } ?: "")
    fun taskDetail(taskId: Int): String = "battle-plan/task/$taskId"
    fun projectDetail(projectId: Int): String = "battle-plan/project/$projectId"
    fun recurringDetail(templateId: Int): String = "battle-plan/recurring/$templateId"
    fun recurringEdit(templateId: Int): String = "battle-plan/recurring/$templateId/edit"
    fun taskDeepLink(taskId: Int): String = "timebox://battle-plan/task/$taskId"
}
