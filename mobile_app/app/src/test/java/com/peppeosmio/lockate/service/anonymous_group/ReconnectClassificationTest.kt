package com.peppeosmio.lockate.service.anonymous_group

import com.peppeosmio.lockate.exceptions.APIException
import com.peppeosmio.lockate.exceptions.LocationDisabledException
import com.peppeosmio.lockate.exceptions.LocationTimeoutException
import com.peppeosmio.lockate.exceptions.NoPermissionException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class ReconnectClassificationTest {

    // classifyReconnect

    @Test
    fun `LocationDisabledException waits for location, even when network is up`() {
        assertEquals(
            ReconnectAction.AwaitLocationEnabled,
            classifyReconnect(LocationDisabledException(), isNetworkAvailable = true)
        )
    }

    @Test
    fun `NoPermissionException waits for location, winning over a missing network`() {
        assertEquals(
            ReconnectAction.AwaitLocationEnabled,
            classifyReconnect(NoPermissionException("perm"), isNetworkAvailable = false)
        )
    }

    @Test
    fun `missed pong with no network waits for the network to return`() {
        // the OkHttp ping surfaces both a dead network and a dead backend as SocketTimeoutException;
        // with no network the fault is the network.
        assertEquals(
            ReconnectAction.AwaitNetwork,
            classifyReconnect(SocketTimeoutException("no pong"), isNetworkAvailable = false)
        )
    }

    @Test
    fun `missed pong while network is up is treated as a backend fault and backs off`() {
        assertEquals(
            ReconnectAction.Backoff,
            classifyReconnect(SocketTimeoutException("no pong"), isNetworkAvailable = true)
        )
    }

    @Test
    fun `location timeout while network is up backs off`() {
        assertEquals(
            ReconnectAction.Backoff,
            classifyReconnect(LocationTimeoutException(), isNetworkAvailable = true)
        )
    }

    @Test
    fun `generic connect failure with no network waits for the network`() {
        assertEquals(
            ReconnectAction.AwaitNetwork,
            classifyReconnect(ConnectException("refused"), isNetworkAvailable = false)
        )
    }

    @Test
    fun `backend API error while network is up backs off`() {
        assertEquals(
            ReconnectAction.Backoff,
            classifyReconnect(APIException(statusCode = 500, body = "boom"), isNetworkAvailable = true)
        )
    }

    @Test
    fun `generic IO error while network is up backs off`() {
        assertEquals(
            ReconnectAction.Backoff,
            classifyReconnect(IOException("boom"), isNetworkAvailable = true)
        )
    }

    // nextBackoffSeconds

    @Test
    fun `backoff grows by 5s per retry`() {
        assertEquals(5L, nextBackoffSeconds(1))
        assertEquals(10L, nextBackoffSeconds(2))
        assertEquals(15L, nextBackoffSeconds(3))
    }

    @Test
    fun `backoff is capped at 60s`() {
        assertEquals(60L, nextBackoffSeconds(12))
        assertEquals(60L, nextBackoffSeconds(1000))
    }
}
