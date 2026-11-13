package com.domrei.gameengine.log

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages the persistent log file system under `Android/data/<package>/files/logs/`.
 *
 * Files written in `Android/data/com.domrei.launcher/files/logs/`:
 * - `latest.log`: Real-time combined log of the active/latest game engine & JVM session.
 * - `session-<yyyyMMdd-HHmmss>-<instance>.log`: Timestamped session log history (auto-rotated).
 */
class EngineLogManager(
    val logsDir: File
) {
    constructor(context: Context) : this(
        logsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
    )

    val latestLogFile: File
        get() = File(logsDir, LATEST_LOG_FILE_NAME)

    @Volatile
    var activeSessionLogFile: File? = null
        private set

    init {
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
    }

    /**
     * Starts a new log session for `instanceName`, resetting `latest.log` and creating
     * a new timestamped session log file under `Android/data/.../files/logs/`.
     */
    @Synchronized
    fun startNewSession(instanceName: String): File {
        logsDir.mkdirs()
        rotateOldSessionLogs()

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeInstance = instanceName
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "minecraft" }

        val sessionFile = File(logsDir, "session-$timestamp-$safeInstance.log")
        activeSessionLogFile = sessionFile

        runCatching {
            latestLogFile.writeText("")
            sessionFile.writeText("")
        }

        val header = "=== Domrei Game Engine Log ($instanceName) [$timestamp] ==="
        appendLogLine(header)
        return latestLogFile
    }

    /**
     * Appends a line to `Android/data/.../files/logs/latest.log` and the active session log file.
     */
    @Synchronized
    fun appendLogLine(line: String, writeToLatest: Boolean = true) {
        logsDir.mkdirs()
        if (writeToLatest) {
            appendToFile(latestLogFile, line)
        }
        activeSessionLogFile?.let { sessionFile ->
            appendToFile(sessionFile, line)
        }
    }

    /**
     * Lists all log files currently stored in `Android/data/.../files/logs/`, newest first.
     */
    fun listLogFiles(): List<File> {
        if (!logsDir.isDirectory) return emptyList()
        return logsDir.listFiles { file -> file.isFile && file.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    private fun appendToFile(file: File, line: String) {
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(line)
                writer.newLine()
            }
        }
    }

    private fun rotateOldSessionLogs() {
        runCatching {
            val sessionFiles = logsDir.listFiles { file ->
                file.isFile && file.name.startsWith("session-") && file.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() }.orEmpty()

            if (sessionFiles.size >= MAX_SESSION_LOGS) {
                sessionFiles.drop(MAX_SESSION_LOGS - 1).forEach { it.delete() }
            }
        }
    }

    companion object {
        const val LATEST_LOG_FILE_NAME = "latest.log"
        private const val MAX_SESSION_LOGS = 20
    }
}
