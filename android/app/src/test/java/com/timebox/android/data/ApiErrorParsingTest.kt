package com.timebox.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorParsingTest {
    @Test
    fun `string detail remains actionable`() {
        val error = apiErrorException(409, """{"detail":"Task type is still used by Battle Plan tasks or recurring templates"}""").error
        assertEquals(ApiErrorCode.TaskTypeReferenceConflict, error.code)
        assertEquals(409, error.statusCode)
    }

    @Test
    fun `backfill detail retains typed counts`() {
        val error = apiErrorException(
            409,
            """{"detail":{"code":"backfill_confirmation_required","past_cycles":3,"past_tasks":12}}""",
        ).error
        assertEquals(ApiErrorCode.BackfillConfirmationRequired, error.code)
        val detail = error.detail as ServerErrorDetail.BackfillConfirmation
        assertEquals(3, detail.pastCycles)
        assertEquals(12, detail.pastTasks)
    }

    @Test
    fun `validation array and unknown bodies do not crash parsing`() {
        val validation = apiErrorException(
            422,
            """{"detail":[{"loc":["body","title"],"msg":"Field required","type":"missing"}]}""",
        ).error
        val issues = (validation.detail as ServerErrorDetail.Validation).issues
        assertEquals(listOf("body", "title"), issues.single().location)
        assertTrue(validation.message.contains("Field required"))

        val unknown = apiErrorException(500, "not-json").error
        assertTrue(unknown.detail is ServerErrorDetail.Unknown)
        assertEquals("Server error (500).", unknown.message)
    }
}
