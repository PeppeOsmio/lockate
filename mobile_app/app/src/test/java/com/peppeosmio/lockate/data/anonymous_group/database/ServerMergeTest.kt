package com.peppeosmio.lockate.data.anonymous_group.database

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerMergeTest {
    @Test
    fun `different servers stay, with their address normalized`() {
        val merge =
            mergeServers(
                listOf(
                    StoredServer(1, "Lockate", "https://Lockate.example.com/", "a"),
                    StoredServer(2, "Home", "http://192.168.1.20:3118", null),
                )
            )
        assertEquals(
            listOf(
                StoredServer(1, "Lockate", "https://lockate.example.com", "a"),
                StoredServer(2, "Home", "http://192.168.1.20:3118", null),
            ),
            merge.kept,
        )
        assertEquals(emptyMap<Long, Long>(), merge.movedTo)
    }

    @Test
    fun `the same server saved twice becomes the oldest, with the newest API key`() {
        val merge =
            mergeServers(
                listOf(
                    StoredServer(5, "Copy", "https://lockate.example.com:443", "new"),
                    StoredServer(2, "Lockate", "https://lockate.example.com", "old"),
                    StoredServer(7, "Again", "HTTPS://LOCKATE.EXAMPLE.COM/", null),
                )
            )
        assertEquals(
            listOf(StoredServer(2, "Lockate", "https://lockate.example.com", "new")),
            merge.kept,
        )
        assertEquals(mapOf(5L to 2L, 7L to 2L), merge.movedTo)
    }
}
