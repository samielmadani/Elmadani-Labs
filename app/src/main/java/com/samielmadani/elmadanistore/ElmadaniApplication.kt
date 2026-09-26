package com.samielmadani.elmadanistudio

import android.app.Application
import android.util.Log

class ElmadaniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashReporter.save(this, throwable)
            Log.e("ElmadaniApplication", "Unhandled exception", throwable)
            defaultHandler?.uncaughtException(thread, throwable) ?: run {
                kotlin.system.exitProcess(1)
            }
        }
    }
}
