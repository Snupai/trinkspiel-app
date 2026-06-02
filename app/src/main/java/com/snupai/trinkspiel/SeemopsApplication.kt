package com.snupai.trinkspiel

import android.app.Application
import com.snupai.trinkspiel.util.AppDiagnostics

class SeemopsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDiagnostics.installCrashHandler(this)
    }
}
