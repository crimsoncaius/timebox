package com.timebox.android.ui.assistant

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class AssistantTaskPresentationTest {
    private fun fixture(name: String) = Json.parseToJsonElement(javaClass.getResource("/assistant-tasks.json")!!.readText()).jsonObject.getValue(name).jsonObject

    @Test fun receiptMatchesSavedIdsAndCreatedReferencesWithoutDependingOnOrder() {
        val proposal = TaskChangeProposal.parse(fixture("proposal"))
        val result = TaskOperationResult.parse(fixture("result"))
        val changes = result.receipt!!.getValue("changes").jsonArray.reversed()
        assertEquals("Review second appendix", receiptTarget(result, changes.first().jsonObject, proposal)?.title)
        assertEquals(proposal.targets.first().title, receiptTarget(result, changes.last().jsonObject, proposal)?.title)
        assertNull(receiptTarget(result, changes.first().jsonObject, proposal.copy(id = "different-review")))
        assertNull(receiptTarget(result, changes.first().jsonObject, proposal.copy(operationId = "different-operation")))
        assertNull(receiptTarget(result, changes.first().jsonObject, null))
        val unmapped = result.copy(receipt = JsonObject(result.receipt - "created"))
        assertNull(receiptTarget(unmapped, changes.first().jsonObject, proposal))
    }

    @Test fun deadlinesKeepDateAndInstantMeaningAndDescriptionNeverLeaks() {
        val zone = ZoneId.of("Asia/Singapore")
        val instant = buildJsonObject { put("kind", "instant"); put("instant", "2026-10-08T23:15:00Z") }
        val date = buildJsonObject { put("kind", "date"); put("date", "2026-10-08") }
        assertEquals("9 Oct 2026, 07:15 · Asia/Singapore", taskDisplayValue(instant, "deadline", zone))
        assertEquals("Thu, 8 Oct 2026", taskDisplayValue(date, "deadline", zone))
        assertEquals("Description changed · separate review", taskDisplayValue(JsonPrimitive("private prose"), "description", zone))
        assertEquals("None", taskDisplayValue(JsonNull, "deadline", zone))
    }

    @Test fun creationSummaryHidesImplicitDefaultsButKeepsExplicitDefaultsAndEffects() {
        val original = TaskChangeProposal.parse(fixture("proposal"))
        val fields = buildJsonObject {
            put("title", "Report"); put("status", "open"); put("deadline", JsonNull)
            put("ready_to_plan", false); put("project_id", JsonNull); put("urgency", JsonNull)
            put("blocked", true); put("blocking_reason", "Waiting for Finance")
        }
        val target = TaskReviewTarget(buildJsonObject { put("ref", "draft") }, "ordinary", "Report", null, null, fields, emptyList())
        val raw = JsonObject(original.raw + ("creation_fields" to buildJsonObject {
            putJsonArray("draft") { add("title"); add("deadline"); add("blocking_reason") }
        }))
        val proposal = original.copy(targets = listOf(target), raw = raw)
        assertTrue(proposal.creationOnly)
        assertEquals(setOf("title", "deadline", "blocked", "blocking_reason"), creationVisibleFields(proposal, target))
        assertEquals("Create task", taskConfirmationLabel(proposal))
        assertEquals("Create 2 tasks", taskConfirmationLabel(proposal.copy(targets = listOf(target, target.copy(title = "Milk")))))
        assertTrue(creationVisibleFields(proposal, target, JsonObject(fields + ("ready_to_plan" to JsonPrimitive(true)))).contains("ready_to_plan"))
    }
}
