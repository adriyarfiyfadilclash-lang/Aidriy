package com.aidriy.app

import android.app.Application
import com.aidriy.app.data.reminders.Notifications
import com.aidriy.app.di.AppContainer
import com.aidriy.app.di.DefaultAppContainer

class AidriyApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        Notifications.ensureChannel(this)
        container.appLock.start()
        container.syncManager.start()
    }
}
