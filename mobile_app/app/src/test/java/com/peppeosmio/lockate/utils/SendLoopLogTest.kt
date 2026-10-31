package com.peppeosmio.lockate.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.concurrent.thread

class SendLoopLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `appends timestamped lines`() {
        val file = File(tmp.root, "send-loop.log")
        val log = SendLoopLog(file)

        log.log("first")
        log.log("second")

        val lines = file.readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].matches(Regex("""\d{4}-\d{2}-\d{2}T\S+Z first""")))
        assertTrue(lines[1].endsWith(" second"))
    }

    @Test
    fun `rotates to a single backup once past the size cap`() {
        val file = File(tmp.root, "send-loop.log")
        val log = SendLoopLog(file, maxBytes = 200)

        repeat(30) { log.log("line $it") }

        val backup = File(tmp.root, "send-loop.log.1")
        assertTrue(backup.exists())
        assertTrue(file.length() <= 200 + 100)
        assertTrue(backup.length() <= 200 + 100)
        assertFalse(File(tmp.root, "send-loop.log.2").exists())
        assertTrue(file.readLines().last().endsWith(" line 29"))
    }

    @Test
    fun `concurrent writers lose no lines`() {
        val file = File(tmp.root, "send-loop.log")
        val log = SendLoopLog(file)

        (0 until 8).map { t -> thread { repeat(100) { log.log("t$t-$it") } } }.forEach { it.join() }

        val lines = file.readLines()
        assertEquals(800, lines.size)
        assertTrue(lines.all { it.matches(Regex("""\S+ t\d-\d+""")) })
    }
}
