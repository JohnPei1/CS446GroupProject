package com.example.wardrobeapp

import android.app.Application

/**
 * Custom Application class that provides access to the [AppContainer].
 */
class WardrobeApplication : Application() {

    /**
     * AppContainer instance used by the rest of the classes to get dependencies.
     */
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppDataContainer(this)
    }
}
