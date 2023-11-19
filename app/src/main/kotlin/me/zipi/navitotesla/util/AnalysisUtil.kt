package me.zipi.navitotesla.util

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AnalysisUtil {
    private val firebaseCrashlytics = FirebaseCrashlytics.getInstance()
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var externalDir: String? = null
    fun initialize(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        externalDir = if (context.getExternalFilesDir(null) != null) {
            context.getExternalFilesDir(null).toString()
        } else {
            null
        }
    }

    fun logEvent(event: String, param: Bundle) {
        firebaseAnalytics?.logEvent(event, param)
    }

    fun log(message: String) {
        firebaseCrashlytics.log(message)
        appendLog("INFO", message)
    }

    fun info(message: String) {
        firebaseCrashlytics.log(message)
        appendLog("INFO", message)
    }

    fun warn(message: String) {
        firebaseCrashlytics.log(message)
        appendLog("WARN", message)
    }

    fun error(message: String) {
        firebaseCrashlytics.log(message)
        appendLog("ERROR", message)
    }

    val isWritableLog: Boolean
        get() = externalDir != null

    fun recordException(e: Throwable) {
        firebaseCrashlytics.recordException(e)
        PrintWriter(StringWriter()).use { writer ->
            e.printStackTrace(writer)
            appendLog("WARN", e.toString() + System.lineSeparator() + writer)
        }
    }

    fun setCustomKey(key: String, value: String) {
        firebaseCrashlytics.setCustomKey(key, value)
    }

    fun sendUnsentReports() {
        firebaseCrashlytics.sendUnsentReports()
    }

    val logFilePath: String
        get() = File("$externalDir/NaviToTesla.log").toString()

    private fun appendLog(logLevel: String, message: String) {
        Log.i(AnalysisUtil::class.java.name, message)
        val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(
            Calendar.getInstance().time
        )
        val text = String.format("%s %s %s", dateTime, logLevel, message)
        if (externalDir != null && !File(externalDir!!).exists() && !File(externalDir!!).mkdirs()) {
            firebaseCrashlytics.log("create document directory fail")
            return
        }
        if (!isWritableLog) {
            return
        }
        val file = File("$externalDir/NaviToTesla.log")
        if (file.exists() && file.length() > 50 * 1024) {
            file.delete()
        }
        try {
            BufferedWriter(FileWriter(file, true)).use { buf ->
                buf.append(text)
                buf.newLine()
            }
        } catch (ignore: FileNotFoundException) {
        } catch (e: IOException) {
            firebaseCrashlytics.recordException(e)
        }
    }

    val logFileSize: Long
        get() {
            val file = File("$externalDir/NaviToTesla.log")
            return if (!file.exists()) {
                0L
            } else file.length()
        }

    fun deleteLogFile() {
        val file = File("$externalDir/NaviToTesla.log")
        if (file.exists()) {
            file.delete()
        }
    }

    fun makeToast(context: Context?, text: String) {
        try {
            Log.i(AnalysisUtil::class.java.name, text)
            log(text)
            Handler(Looper.getMainLooper()).post {
                if (context != null) {
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            recordException(e)
            e.printStackTrace()
        }
    }
}