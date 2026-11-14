package com.timewallet.mvp

import android.app.Application
import com.timewallet.mvp.data.diagnostics.CrashRecorder
import java.io.File

class TimeWalletApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashRecorder.install(File(filesDir, "last_crash.txt"))
    }
}
