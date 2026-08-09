package com.timebox.android

import android.app.Application
import com.timebox.android.data.AppPreferences
import com.timebox.android.data.TimeboxRepository

/** Manual DI: the app has one repository and one preference store. */
class TimeboxApplication : Application() {

    lateinit var repository: TimeboxRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = TimeboxRepository(AppPreferences(this))
    }
}
