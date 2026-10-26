package com.timebox.android.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

class GoogleCalendarFailureTest {
    @Test fun failuresOfferRecoveryWithoutExposingTransportDetails() {
        assertEquals("Cannot reach Timebox. Check your connection and try again.", calendarFailure(IOException("internal host:8001")))
        val denied = HttpException(Response.error<Any>(401, "secret diagnostic".toResponseBody()))
        assertEquals("Timebox could not verify this device. Check the API key under Advanced in Settings.", calendarFailure(denied))
        assertFalse(calendarFailure(IllegalStateException("raw parser output")).contains("raw parser"))
    }
}
