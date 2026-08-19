package com.timebox.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

class TimeboxApiContractTest {
    @Test
    fun `Battle Plan and recurring methods retain backend paths and verbs`() {
        val expected = mapOf(
            "listProjects" to "GET projects",
            "createProject" to "POST projects",
            "patchProject" to "PATCH projects/{projectId}",
            "deleteProject" to "DELETE projects/{projectId}",
            "listBattleTasks" to "GET tasks",
            "createBattleTask" to "POST tasks",
            "patchBattleTask" to "PATCH tasks/{taskId}",
            "reorderBattleTasks" to "POST tasks/reorder",
            "archiveCompletedBattleTasks" to "POST tasks/archive-completed",
            "unarchiveBattleTask" to "POST tasks/{taskId}/unarchive",
            "trashBattleTask" to "DELETE tasks/{taskId}",
            "restoreBattleTask" to "POST tasks/{taskId}/restore",
            "permanentlyDeleteBattleTask" to "DELETE tasks/{taskId}/permanent",
            "listDueReminders" to "GET reminders/due",
            "acknowledgeReminder" to "POST reminders/{taskId}/delivered",
            "previewRecurrence" to "POST recurring-templates/preview",
            "listRecurringTemplates" to "GET recurring-templates",
            "createRecurringTemplate" to "POST recurring-templates",
            "getRecurringTemplate" to "GET recurring-templates/{templateId}",
            "patchRecurringTemplate" to "PATCH recurring-templates/{templateId}",
            "pauseRecurringTemplate" to "POST recurring-templates/{templateId}/pause",
            "resumeRecurringTemplate" to "POST recurring-templates/{templateId}/resume",
            "endRecurringTemplate" to "POST recurring-templates/{templateId}/end",
            "deleteRecurringTemplate" to "DELETE recurring-templates/{templateId}",
        )
        val actual = TimeboxApi::class.java.declaredMethods.mapNotNull { method ->
            val contract = method.getAnnotation(GET::class.java)?.let { "GET ${it.value}" }
                ?: method.getAnnotation(POST::class.java)?.let { "POST ${it.value}" }
                ?: method.getAnnotation(PATCH::class.java)?.let { "PATCH ${it.value}" }
                ?: method.getAnnotation(DELETE::class.java)?.let { "DELETE ${it.value}" }
            contract?.let { method.name to it }
        }.toMap().filterKeys(expected::containsKey)

        assertEquals(expected, actual)
    }
}
