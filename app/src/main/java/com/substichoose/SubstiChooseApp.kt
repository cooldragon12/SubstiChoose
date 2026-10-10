package com.substichoose

import android.app.Application

class SubstiChooseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.nutrition.start()
    }
}
