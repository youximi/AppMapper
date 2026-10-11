package dev.youximi.appmapper.data

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AppLogger {
    private const val FileName = "appmapper.log"
    private const val MaxChars = 160_000
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Synchronized
    fun write(context: Context, message: String, throwable: Throwable? = null) {
        val file = logFile(context)
        file.parentFile?.mkdirs()
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val detail = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
        file.appendText("[$timestamp] $message$detail\n", Charsets.UTF_8)
        trimIfNeeded(file)
    }

    @Synchronized
    fun read(context: Context): String {
        val file = logFile(context)
        if (!file.exists()) return ""
        return file.readText(Charsets.UTF_8)
    }

    @Synchronized
    fun clear(context: Context) {
        logFile(context).writeText("", Charsets.UTF_8)
    }

    private fun logFile(context: Context): File =
        File(context.applicationContext.filesDir, FileName)

    private fun trimIfNeeded(file: File) {
        if (!file.exists()) return
        val text = file.readText(Charsets.UTF_8)
        if (text.length <= MaxChars) return
        file.writeText("[older logs trimmed]\n${text.takeLast(MaxChars)}", Charsets.UTF_8)
    }
}
