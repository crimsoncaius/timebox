package com.timebox.android.data

import com.timebox.android.quicktracking.*
import com.timebox.android.data.remote.ApiFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class QuickFailureTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test fun runtimeIdentityAndTimestampAreEncodedWithoutConsultingDefaultValues() {
        val stored = mutableMapOf<String, String>()
        val encoder = object : AbstractEncoder() {
            override val serializersModule = EmptySerializersModule()
            private var field = ""
            override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean {
                val name = descriptor.getElementName(index)
                // A fresh clock/UUID comparison is never a valid reason to omit these fields.
                assertFalse("Runtime default consulted for $name", name in setOf("id", "occurredAt"))
                return false
            }
            override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
                field = descriptor.getElementName(index)
                return true
            }
            override fun encodeString(value: String) { stored[field] = value }
        }
        val original = QuickFailure(endpoint = "server", deviceId = "device")
        QuickFailure.serializer().serialize(encoder, original)
        assertEquals(original.id, stored["id"])
        assertEquals(original.occurredAt, stored["occurredAt"])
    }

    @Test fun newlyCreatedFailuresAlwaysPersistTheirTimestampAndIdentity() {
        repeat(1000) {
            val original = QuickFailure(endpoint = "server", deviceId = "device")
            val encoded = ApiFactory.json.encodeToString(listOf(original))
            val stored = ApiFactory.json.parseToJsonElement(encoded).jsonArray.single().jsonObject
            assertEquals("Timestamp missing from durable history", original.occurredAt, stored["occurredAt"]?.jsonPrimitive?.content)
            assertEquals(original.id, stored["id"]?.jsonPrimitive?.content)
            assertEquals(listOf(original), ApiFactory.json.decodeFromString<List<QuickFailure>>(encoded))
        }
    }
    @Test fun durableHistoryRetainsOriginalWordsReasonAndRequestIdentityButDoesNotUploadServerCredentials() {
        val original = QuickFailure(endpoint = "private-server-identity", deviceId = "installation", command = "Lunch ten minutes ago",
            reason = "Network unavailable", status = "failed", requestIds = listOf("original-request"),
            history = listOf(QuickFailureEvent("2026-10-29T10:00:00Z", "Lunch ten minutes ago", "Network unavailable")))
        val restored = ApiFactory.json.decodeFromString<QuickFailure>(ApiFactory.json.encodeToString(original))
        assertEquals(original, restored)
        val uploaded = restored.report().toString()
        assertTrue(uploaded.contains("original-request"))
        assertFalse(uploaded.contains("private-server-identity"))
        assertFalse(uploaded.contains("uploadedRevision"))
    }
    @Test fun retryExpiryRespectsFifteenMinutesAndReportingDay() {
        val record = QuickFailure(endpoint = "server", deviceId = "device", occurredAt = "2026-10-29T15:55:00Z")
        val restored = ApiFactory.json.decodeFromString<QuickFailure>(ApiFactory.json.encodeToString(record))
        assertEquals(record.occurredAt, restored.report()["occurred_at"]!!.jsonPrimitive.content)
        assertFalse(restored.expired(Instant.parse("2026-10-29T15:59:00Z"), "Asia/Singapore"))
        assertTrue(restored.expired(Instant.parse("2026-10-29T16:01:00Z"), "Asia/Singapore"))
        assertTrue(restored.expired(Instant.parse("2026-10-29T16:11:00Z"), "UTC"))
    }
    @Test fun diagnosticsKeepRootCauseFramesAndSpeechCodeWithoutExceptionMessages() {
        val error = java.io.IOException("Cannot connect", IllegalStateException("Authorization Bearer SECRET"))
        val diagnostic = quickDiagnostic(error, "interpretation", null, "test", "36")
        assertEquals("java.lang.IllegalStateException", diagnostic["exception_type"]!!.jsonPrimitive.content)
        assertTrue(diagnostic["frames"]!!.jsonArray.isNotEmpty())
        assertFalse(diagnostic.toString().contains("SECRET"))
        assertEquals(7, quickDiagnostic(SpeechCaptureException(7), "capture", null, "test", "36")["error_code"]!!.jsonPrimitive.int)
    }
}
