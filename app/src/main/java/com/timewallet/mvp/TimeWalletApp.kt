package com.timewallet.mvp

import android.app.Application
import androidx.core.content.pm.PackageInfoCompat
import com.timewallet.mvp.data.diagnostics.CrashRecorder
import java.io.File

class TimeWalletApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val installedVersion = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            val name = info.versionName?.takeIf { it.isNotBlank() }
            val code = PackageInfoCompat.getLongVersionCode(info)
            if (name != null) "$name ($code)" else null
        } catch (_: Throwable) {
            null
        }
        CrashRecorder.install(File(filesDir, "last_crash.txt"), appVersion = installedVersion)
    }
}
