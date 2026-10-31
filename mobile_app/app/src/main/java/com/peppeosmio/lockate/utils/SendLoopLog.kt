package com.peppeosmio.lockate.utils

import java.io.File
import java.time.Instant

class SendLoopLog(private val file: File, private val maxBytes: Long = 512 * 1024) {

    @Synchronized
    fun log(message: String) {
        try {
            file.parentFile?.mkdirs()
            if (file.length() > maxBytes) {
                val backup = File(file.path + ".1")
                backup.delete()
                file.renameTo(backup)
            }
            file.appendText("${Instant.now()} $message\n")
        } catch (_: Exception) {
        }
    }
}
