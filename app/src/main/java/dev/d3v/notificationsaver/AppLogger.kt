package dev.d3v.notificationsaver

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AppLogger(
    private val logFile: File,
    private val scope: CoroutineScope,
) {
    private val fileLock = Any()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    init {
        logFile.parentFile?.mkdirs()
    }

    fun info(tag: String, message: String) {
        write(Log.INFO, tag, message, null, persistToFile = true)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        write(Log.WARN, tag, message, throwable, persistToFile = true)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        write(Log.ERROR, tag, message, throwable, persistToFile = true)
    }

    fun logDirectory(): File? = logFile.parentFile

    private fun write(
        level: Int,
        tag: String,
        message: String,
        throwable: Throwable?,
        persistToFile: Boolean,
    ) {
        val printable = if (throwable == null) {
            message
        } else {
            "$message\n${Log.getStackTraceString(throwable)}"
        }
        Log.println(level, tag, printable)
        if (!persistToFile) {
            return
        }
        val line = buildString {
            append(formatter.format(Instant.now()))
            append(" [")
            append(levelName(level))
            append("] ")
            append(tag)
            append(": ")
            append(printable)
            append('\n')
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                synchronized(fileLock) {
                    logFile.parentFile?.mkdirs()
                    rotateIfNeeded(line.toByteArray().size.toLong())
                    logFile.appendText(line)
                }
            }
        }
    }

    private fun rotateIfNeeded(incomingBytes: Long) {
        if (logFile.exists() && logFile.length() + incomingBytes <= MAX_LOG_FILE_BYTES) {
            return
        }
        val logDir = logFile.parentFile ?: return
        File(logDir, "${logFile.name}.2").delete()
        File(logDir, "${logFile.name}.1").takeIf { it.exists() }?.renameTo(File(logDir, "${logFile.name}.2"))
        logFile.takeIf { it.exists() }?.renameTo(File(logDir, "${logFile.name}.1"))
        logFile.writeText("")
    }

    private fun levelName(level: Int): String = when (level) {
        Log.ERROR -> "ERROR"
        Log.WARN -> "WARN"
        Log.DEBUG -> "DEBUG"
        else -> "INFO"
    }

    companion object {
        private const val MAX_LOG_FILE_BYTES = 512L * 1024L
    }
}
