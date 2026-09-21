package com.timebox.android.ui.day

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.timebox.android.data.AppPreferences
import com.timebox.android.data.DayViewPreferences
import com.timebox.android.data.DayViewSection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DayViewPreferencesTest {
    @Test fun independentWritesSurviveReopeningPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = AppPreferences(context)
        val before = preferences.dayViewPreferences.first()
        assertEquals(DayViewPreferences(true, false), DayViewPreferences())
        try {
            listOf(
                async { preferences.setDaySectionVisible(DayViewSection.Calendar, false) },
                async { preferences.setDaySectionVisible(DayViewSection.Tracking, true) },
            ).awaitAll()
            assertEquals(DayViewPreferences(false, true), AppPreferences(context).dayViewPreferences.first())
            preferences.setDaySectionVisible(DayViewSection.Tracking, false)
            assertEquals(DayViewPreferences(false, false), AppPreferences(context).dayViewPreferences.first())
        } finally {
            preferences.setDaySectionVisible(DayViewSection.Calendar, before.calendar)
            preferences.setDaySectionVisible(DayViewSection.Tracking, before.tracking)
        }
    }
}
