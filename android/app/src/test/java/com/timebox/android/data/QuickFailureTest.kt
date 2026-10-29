package com.timebox.android.data

import com.timebox.android.quicktracking.*
import com.timebox.android.data.remote.ApiFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class QuickFailureTest {
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
        assertFalse(record.expired(Instant.parse("2026-10-29T15:59:00Z"), "Asia/Singapore"))
        assertTrue(record.expired(Instant.parse("2026-10-29T16:01:00Z"), "Asia/Singapore"))
        assertTrue(record.expired(Instant.parse("2026-10-29T16:11:00Z"), "UTC"))
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
