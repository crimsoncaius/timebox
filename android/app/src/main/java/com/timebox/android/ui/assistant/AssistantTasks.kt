package com.timebox.android.ui.assistant

import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.let { check(it.isString); it.content }
internal fun JsonObject.optionalText(key: String): String? = get(key)?.takeUnless { it == JsonNull }?.jsonPrimitive?.let { check(it.isString); it.content }
internal fun JsonObject.uuid(key: String): String = text(key).also { check(UUID.fromString(it).toString() == it) }
internal fun JsonObject.integer(key: String): Int = getValue(key).jsonPrimitive.let { check(!it.isString); it.int }
internal fun JsonObject.flag(key: String): Boolean = getValue(key).jsonPrimitive.let { check(!it.isString); it.boolean }
internal fun JsonObject.bounded(bytes: Int) { check(toString().toByteArray(Charsets.UTF_8).size <= bytes) { "Task payload exceeds its bound" } }
internal fun JsonObject.only(vararg keys: String) { check(this.keys.all { it in keys }) { "Unsupported task payload field" } }
private val taskKinds = setOf("ordinary", "occurrence", "quota_tracker", "session")
internal val taskLifecycles = setOf("draft", "pending", "applied", "stale", "cancelled", "replaced", "expired", "invalid")
private val reviewFields = setOf("title", "description", "project_id", "task_type_id", "urgency", "importance", "deadline", "reminder_at", "blocked", "blocking_reason", "ready_to_plan", "status", "completed_at", "completion_precision", "completion_local_date", "completion_timezone", "completion", "checked")

/** A public projection: private text and internal guards cannot enter this model. */
data class AssistantTaskRow(val id: Int, val kind: String, val availability: String, val values: JsonObject) {
    val title get() = values.optionalText("title") ?: "Task #$id"
    companion object {
        fun parse(row: JsonObject): AssistantTaskRow {
            row.only("id", "kind", "availability", "title", "lifecycle", "relevance", "project", "task_type", "ready_to_plan", "blocked", "blocking_reason", "urgency", "importance", "deadline", "reminder_at", "completed_at", "completion_precision", "completion_local_date", "completion_timezone", "parent_id", "series_id", "period", "quota", "subtasks", "subtasks_count", "subtasks_next_cursor", "sessions", "sessions_next_cursor", "matched_subtask_ids", "planned_dates", "planned_dates_complete")
            val id = row.integer("id").also { check(it > 0) }
            val kind = row.text("kind").also { check(it in taskKinds) }
            val available = row.text("availability").also { check(it in setOf("available", "unavailable", "unverified")) }
            if (available != "available") check(row.keys == setOf("id", "kind", "availability"))
            row["title"]?.let { check(row.text("title").isNotBlank()) }
            row.optionalText("lifecycle")?.let { check(it in setOf("incomplete", "completed", "skipped")) }
            listOf("ready_to_plan", "blocked", "planned_dates_complete").forEach { if (it in row) row.flag(it) }
            row["subtasks"]?.jsonArray?.also { check(it.size <= 20) }?.forEach {
                it.jsonObject.apply {
                    only("id", "title", "checked", "availability"); check(integer("id") > 0)
                    if (optionalText("availability") == "unavailable") check(keys == setOf("id", "availability"))
                    else { check(optionalText("availability") in listOf(null, "available")); text("title"); flag("checked") }
                }
            }
            row["sessions"]?.jsonArray?.also { check(it.size <= 20) }?.forEach { parse(it.jsonObject) }
            row["project"]?.takeUnless { it == JsonNull }?.jsonObject?.apply { only("id", "name"); check(integer("id") > 0); text("name") }
            row["task_type"]?.takeUnless { it == JsonNull }?.jsonObject?.apply { only("id", "path"); check(integer("id") > 0); text("path") }
            listOf("reminder_at", "completed_at").forEach { row.optionalText(it)?.let(Instant::parse) }
            row.optionalText("completion_precision")?.let { check(it in setOf("date", "instant", "unknown")) }
            if (row.optionalText("completion_precision") == "date") { LocalDate.parse(row.text("completion_local_date")); ZoneId.of(row.text("completion_timezone")) }
            row["planned_dates"]?.jsonArray?.forEach { LocalDate.parse(it.jsonPrimitive.content) }
            row["deadline"]?.takeUnless { it == JsonNull }?.jsonObject?.apply {
                only("kind", "date", "instant")
                when (text("kind")) { "date" -> { LocalDate.parse(text("date")); check(optionalText("instant") == null) }; "instant" -> { Instant.parse(text("instant")); check(optionalText("date") == null) }; else -> error("Invalid deadline") }
            }
            row["period"]?.jsonObject?.apply { only("start", "end"); check(!LocalDate.parse(text("end")).isBefore(LocalDate.parse(text("start")))) }
            row["quota"]?.jsonObject?.apply { only("required", "completed", "saved_session_count"); keys.forEach { check(integer(it) >= 0) } }
            return AssistantTaskRow(id, kind, available, row)
        }
    }
}

data class AssistantTaskCard(val id: String, val readAt: Instant, val zone: ZoneId, val rows: List<AssistantTaskRow>,
    val count: Int?, val countRelation: String, val partial: Boolean, val limitations: List<String>, val queryAt: Instant?, val hasMore: Boolean) {
    companion object {
        fun parse(data: JsonObject): AssistantTaskCard {
            data.bounded(32 * 1024)
            data.only("run_id", "sequence", "schema_version", "kind", "snapshot_id", "read_at", "reporting_timezone", "query_id", "query_at", "matching_count", "count_relation", "next_cursor", "completeness", "limitations", "rows")
            check(data.integer("schema_version") == 4 && data.text("kind") == "tasks")
            val rows = data.getValue("rows").jsonArray.also { check(it.size <= 20) }.map { AssistantTaskRow.parse(it.jsonObject) }
            check(rows.map { it.id }.distinct().size == rows.size)
            val relation = data.text("count_relation").also { check(it in setOf("exact", "at_least", "unknown")) }
            val count = data["matching_count"]?.takeUnless { it == JsonNull }?.let { data.integer("matching_count").also { check(it >= 0) } }
            check(relation == "unknown" || count != null)
            val completeness = data.text("completeness").also { check(it in setOf("complete", "partial")) }
            val limitations = data.getValue("limitations").jsonArray.map { it.jsonPrimitive.content.also { v -> check(v in setOf("saved_only", "query_limit", "payload_limit", "unverified_readiness", "unavailable")) } }
            return AssistantTaskCard(data.uuid("snapshot_id"), Instant.parse(data.text("read_at")), ZoneId.of(data.text("reporting_timezone")), rows,
                count, relation, completeness == "partial", limitations, data.optionalText("query_at")?.let(Instant::parse), data.optionalText("next_cursor") != null)
        }
    }
}

data class TaskReviewTarget(val target: JsonObject, val kind: String, val title: String, val parent: JsonElement?, val before: JsonObject?, val after: JsonObject, val transitions: List<String>) {
    val id: Int? get() = target["id"]?.jsonPrimitive?.int
    val identity: String get() = id?.let { "#$it" } ?: "New ${target.text("ref")}"
}

/** Immutable reviewed content, separate from lifecycle and execution results. */
data class TaskChangeProposal(val id: String, val operationId: String, val conversationId: String, val runId: String,
    val contentHash: String, val expiresAt: Instant, val zone: ZoneId, val targets: List<TaskReviewTarget>, val effects: JsonObject,
    val references: JsonObject, val descriptionReview: Boolean, val status: String, val sourceCompleted: Boolean,
    val raw: JsonObject) {
    companion object {
        fun parse(data: JsonObject, fullDescription: Boolean = false): TaskChangeProposal {
            data.bounded(64 * 1024)
            data.only("run_id", "sequence", "schema_version", "proposal_id", "revision", "operation_id", "conversation_id", "originating_run_id", "created_at", "expires_at", "request_anchor", "operations", "review_targets", "side_effects", "references", "description_review_available", "parent_ids", "content_hash", "status", "source_completed_at", "supersedes_proposal_id", "request_intent")
            check(fullDescription || "request_intent" !in data)
            check(data.integer("schema_version") == 1 && data.integer("revision") == 1)
            val status = data.text("status").also { check(it in taskLifecycles) }
            val hash = data.text("content_hash").also { check(it.matches(Regex("[0-9a-f]{64}"))) }
            Instant.parse(data.text("created_at"))
            val anchor = data.getValue("request_anchor").jsonObject
            Instant.parse(anchor.text("received_at"))
            val operations = data.getValue("operations").jsonArray.also { check(it.size in 1..20) }
            fun identity(value: JsonElement) = value.jsonObject.apply {
                check(keys == setOf("id") || keys == setOf("ref"))
                if ("id" in this) check(integer("id") > 0) else check(text("ref").isNotBlank())
            }
            operations.forEach { value ->
                val op = value.jsonObject
                when (op.text("op")) {
                    "create_task" -> { check(op.keys.all { it in reviewFields || it in setOf("op", "ref") }); check(op.text("ref").isNotBlank()); check(op.text("title").isNotBlank()) }
                    "patch_task" -> { op.only("op", "target", "set"); identity(op.getValue("target")); check(op.getValue("set").jsonObject.isNotEmpty()); check(op.getValue("set").jsonObject.keys.all { it in reviewFields }) }
                    "add_subtask" -> { op.only("op", "parent", "ref", "title"); identity(op.getValue("parent")); check(op.text("ref").isNotBlank()); check(op.text("title").isNotBlank()) }
                    "rename_subtask" -> { op.only("op", "subtask_id", "parent_id", "title"); check(op.integer("subtask_id") > 0 && op.integer("parent_id") > 0); check(op.text("title").isNotBlank()) }
                    "set_subtask_checked" -> { op.only("op", "target", "parent", "checked"); identity(op.getValue("target")); identity(op.getValue("parent")); op.flag("checked") }
                    "complete_at" -> { op.only("op", "target", "completion"); identity(op.getValue("target")); val completion = op.getValue("completion").jsonObject; check(completion.text("precision") in setOf("date", "instant")); Instant.parse(completion.text("instant")); ZoneId.of(completion.text("reporting_timezone")); if (completion.text("precision") == "date") LocalDate.parse(completion.text("local_date")) }
                    "complete_now", "reopen_task" -> { op.only("op", "target"); identity(op.getValue("target")) }
                    else -> error("Unsupported Task operation")
                }
                val fields = op["set"]?.jsonObject ?: op
                if (!fullDescription) fields["description"]?.jsonObject?.apply { only("sha256", "characters", "bytes", "separate_review"); check(flag("separate_review")) }
            }
            val targets = data.getValue("review_targets").jsonArray.also { check(it.size in 1..25) }.map {
                val row = it.jsonObject
                row.only("target", "kind", "title", "parent", "before", "after", "transitions")
                val target = row.getValue("target").jsonObject
                check(target.keys == setOf("id") || target.keys == setOf("ref"))
                if ("id" in target) check(target.integer("id") > 0) else check(target.text("ref").isNotBlank())
                val before = row["before"]?.takeUnless { v -> v == JsonNull }?.jsonObject
                val after = row.getValue("after").jsonObject
                listOfNotNull(before, after).forEach { fields ->
                    check(fields.keys.all { field -> field in reviewFields })
                    fields["description"]?.let { description ->
                        if (fullDescription) check(description == JsonNull || description.jsonPrimitive.isString)
                        else description.jsonObject.apply { only("sha256", "characters", "bytes", "separate_review"); check(flag("separate_review")); check(integer("characters") >= 0); check(integer("bytes") >= 0); text("sha256") }
                    }
                }
                val kind = row.text("kind").also { check(it in setOf("ordinary", "subtask")) }
                val transitions = row.getValue("transitions").jsonArray.map { v -> v.jsonPrimitive.content.also { t -> check(t in setOf("complete_now", "complete_at", "reopen_task")) } }
                TaskReviewTarget(target, kind, row.text("title"), row["parent"], before, after, transitions)
            }
            check(targets.count { it.kind == "ordinary" } <= 5)
            check(targets.map { it.target }.distinct().size == targets.size)
            val effects = data.getValue("side_effects").jsonObject
            effects.values.forEach { value ->
                value.jsonObject.apply {
                    only("running_actual", "tracking", "removed_plans", "removed_plan_count", "detached_actual_ids", "cleared_fields", "restores_prior_state", "restarts_tracking")
                    get("running_actual")?.takeUnless { it == JsonNull }?.jsonObject?.apply { only("id", "task_id", "task_type_id", "start_at", "planned_block_id"); check(integer("id") > 0); Instant.parse(text("start_at")) }
                    get("removed_plans")?.jsonArray?.forEach { plan -> plan.jsonObject.apply { only("id", "day_id", "task_type_id", "task_id", "name", "start_minute", "end_minute", "start_at", "end_at", "day_date"); check(integer("id") > 0); LocalDate.parse(text("day_date")) } }
                    get("removed_plan_count")?.let { check(integer("removed_plan_count") == getValue("removed_plans").jsonArray.size) }
                    optionalText("tracking")?.let { check(it in setOf("stop", "unchanged")) }
                }
            }
            val references = data.getValue("references").jsonObject
            references.forEach { (key, value) -> value.jsonObject.apply {
                only("id", "name", "path"); check(integer("id") > 0)
                check(key == "project_id:${integer("id")}" || key == "task_type_id:${integer("id")}")
                text(if (key.startsWith("project_id:")) "name" else "path")
            } }
            check(data.flag("description_review_available") == targets.any { "description" in it.after })
            return TaskChangeProposal(data.uuid("proposal_id"), data.uuid("operation_id"), data.uuid("conversation_id"), data.uuid("originating_run_id"), hash,
                Instant.parse(data.text("expires_at")), ZoneId.of(anchor.text("reporting_timezone")), targets, data.getValue("side_effects").jsonObject,
                data.getValue("references").jsonObject, data.flag("description_review_available"), status, data.optionalText("source_completed_at")?.let { Instant.parse(it); true } ?: false, data)
        }
    }
}

data class TaskOperationResult(val operationId: String, val proposalId: String, val status: String, val receipt: JsonObject?,
    val submission: JsonObject?, val undo: JsonObject?, val events: JsonArray) {
    companion object {
        fun parse(data: JsonObject): TaskOperationResult {
            data.bounded(64 * 1024)
            data.only("operation_id", "proposal_id", "status", "receipt", "submission", "status_events", "older_status_events", "undo_result")
            val operationId = data.uuid("operation_id")
            val proposalId = data.uuid("proposal_id")
            val status = data.text("status").also { check(it in taskLifecycles || it == "checking") }
            val receipt = data["receipt"]?.takeUnless { it == JsonNull }?.jsonObject
            receipt?.apply {
                bounded(64 * 1024)
                only("schema_version", "operation_id", "proposal_id", "revision", "content_hash", "conversation_id", "originating_run_id", "committed_at", "outcome", "created", "changes", "effects", "undo", "status_events", "original_operation_id", "target_id", "tracking_restarted")
                check(integer("schema_version") == 1 && uuid("operation_id") == operationId && uuid("proposal_id") == proposalId)
                check(text("outcome") in setOf("applied", "undone")); Instant.parse(text("committed_at"))
                get("changes")?.jsonArray?.forEach { item ->
                    item.jsonObject.apply { only("target_id", "kind", "changed_fields", "approved_after_values"); check(integer("target_id") > 0); check(text("kind") in setOf("ordinary", "subtask")) }
                    val fields = item.jsonObject.getValue("approved_after_values").jsonObject
                    check(fields.keys.all { it in reviewFields })
                    fields["description"]?.jsonObject?.apply { only("sha256", "characters", "bytes", "separate_review"); check(flag("separate_review")); integer("characters"); integer("bytes") }
                }
            }
            receipt?.get("effects")?.jsonObject?.only("completion_instants", "stopped_actual_ids", "removed_plan_ids", "detached_actual_links", "cleared_fields")
            data["undo_result"]?.takeUnless { it == JsonNull }?.jsonObject?.apply {
                only("operation_id", "status", "receipt"); uuid("operation_id"); check(text("status") in setOf("available", "applied", "conflict"))
                if (text("status") == "applied") {
                    val saved = getValue("receipt").jsonObject
                    check(saved.uuid("operation_id") == uuid("operation_id") && saved.uuid("original_operation_id") == operationId)
                    Instant.parse(saved.text("committed_at")); check(saved.text("outcome") == "applied")
                }
            }
            check(status != "applied" || receipt != null)
            val submission = data["submission"]?.takeUnless { it == JsonNull }?.jsonObject
            submission?.apply { uuid("submission_id"); check(uuid("operation_id") == operationId); check(text("state") in setOf("not_seen", "executing", "rolled_back", "rejected", "applied")) }
            return TaskOperationResult(operationId, proposalId, status, receipt, submission,
                data["undo_result"]?.takeUnless { it == JsonNull }?.jsonObject, data.getValue("status_events").jsonArray)
        }
    }
}

/** The write gate is deliberately closed until durable submission/barrier integration is installed. */
const val TaskConfirmationGate = "Task confirmation is unavailable until synchronization and recovery are ready."
data class TaskChangeState(val status: String = "draft", val sourceCompleted: Boolean = false, val result: TaskOperationResult? = null, val busy: Boolean = false, val error: String? = null)

internal fun taskFieldLabel(field: String): String = when (field) {
    "ready_to_plan" -> "Ready to Plan"; "task_type_id" -> "Task Type"; "project_id" -> "Project"; "reminder_at" -> "Reminder"
    "completed_at" -> "Completed"; "completion" -> "Completion"; else -> field.replace('_', ' ').replaceFirstChar { it.titlecase() }
}

/** Literal text. Date precision never exposes the storage-only noon instant. */
internal fun taskValue(value: JsonElement?, field: String = "", references: JsonObject = JsonObject(emptyMap())): String {
    if (field == "description") return "Description changed · separate review"
    if (value == null || value == JsonNull) return "None"
    if (field in setOf("project_id", "task_type_id")) {
        val ref = references["$field:${value.jsonPrimitive.content}"]?.jsonObject
        return ref?.optionalText(if (field == "project_id") "name" else "path")?.let { "$it (#${value.jsonPrimitive.content})" } ?: "#${value.jsonPrimitive.content}"
    }
    return when (value) {
        is JsonPrimitive -> if (value.booleanOrNull != null) if (value.boolean) "Yes" else "No" else value.content
        is JsonArray -> value.joinToString("\n") { taskValue(it) }.ifEmpty { "None" }
        is JsonObject -> when {
            value.optionalText("precision") == "date" -> value.text("local_date") + " · " + value.text("reporting_timezone")
            value.optionalText("kind") == "date" -> value.text("date")
            else -> value.entries.joinToString("\n") { (k, v) -> "${taskFieldLabel(k)}: ${taskValue(v, k, references)}" }
        }
    }
}
