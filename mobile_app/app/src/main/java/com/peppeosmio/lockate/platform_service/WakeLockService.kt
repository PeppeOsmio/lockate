package com.peppeosmio.lockate.platform_service

import android.content.Context
import android.os.PowerManager

class WakeLockService(context: Context) {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    /** Keeps the CPU on while [block] runs, and at most [maxMillis] if it never returns */
    suspend fun <T> held(tag: String, maxMillis: Long, block: suspend () -> T): T {
        // not reference counted, so releasing it after its timeout doesn't throw
        val lock =
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
                setReferenceCounted(false)
            }
        lock.acquire(maxMillis)
        try {
            return block()
        } finally {
            lock.release()
        }
    }
}
