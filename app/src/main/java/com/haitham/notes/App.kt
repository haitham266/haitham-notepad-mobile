package com.haitham.notes

import android.app.Application
import com.haitham.notes.data.Repo
import com.haitham.notes.sync.SyncScheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        if (Repo.prefs.accountEmail != null && Repo.prefs.autoSync) SyncScheduler.scheduleDaily(this)
    }
}
