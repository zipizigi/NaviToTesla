package me.zipi.navitotesla.util

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

object AnalysisUtil {
    private const val LOG_TAG = "NaviToTesla"
    private const val LOG_FILE_NAME = "NaviToTesla.log"
    private const val MAX_LOG_FILE_BYTES: Long = 100L * 1024L
    private const val ROLL_TRIM_BYTES: Long = 10L * 1024L
    private const val BATCH_MAX = 64
    private const val BATCH_LINGER_MS = 200L
    private const val CHANNEL_CAPACITY = 512

    private val firebaseCrashlytics = FirebaseCrashlytics.getInstance()
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var externalDir: String? = null

    private val timestampFormatter =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        }

    private val logChannel =
        Channel<String>(
            capacity = CHANNEL_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val writerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var writerJob: Job? = null

    fun initialize(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        externalDir =
            if (context.getExternalFilesDir(null) != null) {
                context.getExternalFilesDir(null).toString()
            } else {
                null
            }
        startWriterIfNeeded()
    }

    @Synchronized
    private fun startWriterIfNeeded() {
        if (writerJob?.isActive == true) return
        writerJob = writerScope.launch { writerLoop() }
    }

    fun logEvent(
        event: String,
        param: Bundle,
    ) {
        firebaseAnalytics?.logEvent(event, param)
    }

    fun log(message: String) = enqueue("INFO", message)

    fun debug(message: String) = enqueue("DEBUG", message)

    fun info(message: String) = enqueue("INFO", message)

    fun warn(
        message: String,
        e: Throwable? = null,
    ) = enqueue("WARN", withStack(message, e))

    fun error(
        message: String,
        e: Throwable? = null,
    ) = enqueue("ERROR", withStack(message, e))

    private fun withStack(
        message: String,
        e: Throwable?,
    ): String = if (e == null) message else "$message${System.lineSeparator()}${e.stackTraceToString()}"

    fun report(message: String) {
        firebaseCrashlytics.log(message)
        log(message)
    }

    val isWritableLog: Boolean
        get() = externalDir != null

    fun recordException(e: Throwable) {
        if (e !is CancellationException) {
            firebaseCrashlytics.recordException(e)
        }
        PrintWriter(StringWriter()).use { writer ->
            e.printStackTrace(writer)
            enqueue("WARN", e.toString() + System.lineSeparator() + writer)
        }
    }

    fun setCustomKey(
        key: String,
        value: String,
    ) {
        firebaseCrashlytics.setCustomKey(key, value)
    }

    fun sendUnsentReports() {
        firebaseCrashlytics.sendUnsentReports()
    }

    val logFilePath: String
        get() = File("$externalDir/$LOG_FILE_NAME").toString()

    val logFileSize: Long
        get() {
            val file = File("$externalDir/$LOG_FILE_NAME")
            return if (!file.exists()) 0L else file.length()
        }

    fun deleteLogFile() =
        writerScope.launch {
            val dir = externalDir ?: return@launch
            val file = File("$dir/$LOG_FILE_NAME")
            if (file.exists()) {
                file.delete()
            }
        }

    enum class ToastLevel { INFO, WARN, ERROR }

    fun makeToast(
        context: Context?,
        text: String,
        level: ToastLevel = ToastLevel.INFO,
    ) {
        try {
            when (level) {
                ToastLevel.WARN -> warn(text)
                ToastLevel.ERROR -> error(text)
                ToastLevel.INFO -> log(text)
            }
            CoroutineScope(Dispatchers.Main).launch {
                if (context != null) {
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            recordException(e)
            e.printStackTrace()
        }
    }

    private fun enqueue(
        level: String,
        message: String,
    ) {
        val priority =
            when (level) {
                "DEBUG" -> Log.DEBUG
                "WARN" -> Log.WARN
                "ERROR" -> Log.ERROR
                else -> Log.INFO
            }
        Log.println(priority, LOG_TAG, message)
        val line = formatLine(level, message)
        logChannel.trySend(line)
    }

    private fun formatLine(
        level: String,
        message: String,
    ): String {
        val ts = timestampFormatter.get()!!.format(Date())
        val thread = Thread.currentThread().name
        return buildString(message.length + 64) {
            append(ts)
            append(' ')
            append(level.padEnd(5))
            append(" [")
            append(thread)
            append("] ")
            append(message)
        }
    }

    private suspend fun writerLoop() {
        val pending = ArrayList<String>(BATCH_MAX)
        while (writerScope.isActive) {
            pending.clear()
            val first = logChannel.receiveCatching().getOrNull() ?: return
            pending.add(first)

            val deadline = System.currentTimeMillis() + BATCH_LINGER_MS
            while (pending.size < BATCH_MAX) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) break
                val next =
                    withTimeoutOrNull(remaining) {
                        logChannel.receiveCatching().getOrNull()
                    } ?: break
                pending.add(next)
            }

            flush(pending)
        }
    }

    private fun flush(lines: List<String>) {
        val dir = externalDir ?: return
        val parent = File(dir)
        if (!parent.exists() && !parent.mkdirs()) {
            firebaseCrashlytics.log("create document directory fail")
            return
        }
        val file = File(parent, LOG_FILE_NAME)
        try {
            BufferedWriter(FileWriter(file, true)).use { buf ->
                for (line in lines) {
                    buf.append(line)
                    buf.newLine()
                }
            }
            rollIfNeeded(file)
        } catch (_: FileNotFoundException) {
        } catch (e: IOException) {
            firebaseCrashlytics.recordException(e)
        }
    }

    private fun rollIfNeeded(file: File) {
        try {
            val size = file.length()
            if (size <= MAX_LOG_FILE_BYTES) return
            val keepBytes = MAX_LOG_FILE_BYTES - ROLL_TRIM_BYTES
            val rawSkip = (size - keepBytes).toInt().coerceAtLeast(0)
            val bytes = file.readBytes()
            val lf = '\n'.code.toByte()
            var aligned = rawSkip
            while (aligned < bytes.size && bytes[aligned] != lf) {
                aligned++
            }
            if (aligned < bytes.size) aligned++
            if (aligned >= bytes.size) {
                file.writeBytes(ByteArray(0))
            } else {
                file.writeBytes(bytes.copyOfRange(aligned, bytes.size))
            }
        } catch (e: IOException) {
            firebaseCrashlytics.recordException(e)
        } catch (_: OutOfMemoryError) {
            try {
                file.writeBytes(ByteArray(0))
            } catch (_: IOException) {
            }
        }
    }
}
