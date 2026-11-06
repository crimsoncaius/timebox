package com.timebox.android.data

import com.timebox.android.data.remote.ApiFactory
import org.junit.Assert.*
import org.junit.Test

class ConnectionValidationTest {
    @Test fun validHostAndPathAreNormalized() {
        assertEquals("http://10.0.2.2:8001/", ApiFactory.validatedBaseUrl("10.0.2.2:8001"))
        assertEquals("https://example.com/timebox/", ApiFactory.validatedBaseUrl("https://example.com/timebox"))
    }
    @Test fun malformedAddressesAndEmbeddedCredentialsAreRejected() {
        listOf("", "http://", "bad host", "ftp://example.com", "https://user:secret@example.com", "https://example.com?q=x", "https://example.com#fragment").forEach { raw ->
            assertTrue(raw, runCatching { ApiFactory.validatedBaseUrl(raw) }.isFailure)
        }
    }
}
