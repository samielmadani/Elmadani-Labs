package com.samielmadani.elmadanistudio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val FILE_NAME = "crash_log.txt"

    fun save(context: Context, throwable: Throwable) {
        val log = buildLog(throwable)
        val file = File(context.filesDir, FILE_NAME)
        FileOutputStream(file, false).use { stream ->
            stream.write(log.toByteArray(Charsets.UTF_8))
        }
    }

    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    fun emailIntent(context: Context): Intent {
        val report = read(context) ?: "No crash log available."
        val body = "Please describe what you were doing when this happened.\n\n--- Crash log ---\n$report"
        val email = "hello@samielmadani.dev"
        val uri = Uri.parse("mailto:$email?subject=${Uri.encode("Elmadani Studio crash report")}&body=${Uri.encode(body)}")
        return Intent(Intent.ACTION_SENDTO, uri)
    }

    private fun buildLog(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
        return buildString {
            append("Timestamp: ").append(timestamp).appendLine()
            append("App: ").append("Elmadani Studio").appendLine()
            append("Version: ").append(BuildConfig.VERSION_NAME).appendLine()
            append("Build: ").append(BuildConfig.VERSION_CODE).appendLine()
            append("Android: ").append(Build.VERSION.RELEASE).appendLine()
            append("SDK: ").append(Build.VERSION.SDK_INT).appendLine()
            append("Manufacturer: ").append(Build.MANUFACTURER).appendLine()
            append("Model: ").append(Build.MODEL).appendLine()
            appendLine("--- Stack trace ---")
            append(sw.toString())
        }
    }
}
