package com.peppeosmio.lockate.service.anonymous_group

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PostEachTest {
    private var online = true
    private var networkBack = CompletableDeferred<Unit>()
    private val posted = mutableListOf<String>()

    private fun goOnline() {
        online = true
        networkBack.complete(Unit)
    }

    private suspend fun post(item: String): Boolean {
        posted += item
        return online
    }

    @Test
    fun `an item that failed while offline is posted again when the network returns`() = runTest {
        online = false
        val items = Channel<String>()
        val job = launch {
            items.consumeAsFlow().postEach(::post, { online }, { networkBack.await() })
        }
        items.send("still")
        runCurrent()
        assertEquals(listOf("still"), posted)

        goOnline()
        runCurrent()

        assertEquals(listOf("still", "still"), posted)
        job.cancel()
    }

    @Test
    fun `an item that failed with the network there is not posted again`() = runTest {
        flowOf("a")
            .postEach(
                {
                    posted += it
                    false
                },
                { true },
                { networkBack.await() },
            )

        assertEquals(listOf("a"), posted)
    }

    @Test
    fun `a newer item replaces the one waiting for the network`() = runTest {
        online = false
        val items = Channel<String>()
        val job = launch {
            items.consumeAsFlow().postEach(::post, { online }, { networkBack.await() })
        }
        items.send("old")
        runCurrent()
        online = true
        items.send("new")
        runCurrent()

        goOnline()
        runCurrent()

        assertEquals(listOf("old", "new"), posted)
        job.cancel()
    }

    @Test
    fun `a newer item waits for the resend already posting`() = runTest {
        online = false
        val items = Channel<String>()
        val job = launch {
            items
                .consumeAsFlow()
                .postEach(
                    { item ->
                        posted += "start $item"
                        if (item == "old" && online) delay(1_000)
                        posted += "end $item"
                        online
                    },
                    { online },
                    { networkBack.await() },
                )
        }
        items.send("old")
        runCurrent()
        goOnline()
        runCurrent()
        items.send("new")
        runCurrent()
        delay(2_000)

        assertEquals(
            listOf("start old", "end old", "start old", "end old", "start new", "end new"),
            posted,
        )
        job.cancel()
    }
}
