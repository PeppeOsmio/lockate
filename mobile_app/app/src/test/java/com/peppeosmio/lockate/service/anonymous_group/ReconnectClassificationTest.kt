package com.peppeosmio.lockate.service.anonymous_group

import com.peppeosmio.lockate.exceptions.AGMemberUnauthorizedException
import com.peppeosmio.lockate.exceptions.APIException
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import io.ktor.client.plugins.sse.SSEClientException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class ReconnectClassificationTest {

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

    // classifyStreamFailure

    @Test
    fun `stream closed normally while network is up backs off`() {
        assertEquals(StreamRetryAction.Backoff, classifyStreamFailure(null, isNetworkAvailable = true))
    }

    @Test
    fun `stream closed normally with no network waits for the network`() {
        assertEquals(
            StreamRetryAction.AwaitNetwork, classifyStreamFailure(null, isNetworkAvailable = false)
        )
    }

    @Test
    fun `member unauthorized stops the stream, even with no network`() {
        assertEquals(
            StreamRetryAction.Stop,
            classifyStreamFailure(AGMemberUnauthorizedException(), isNetworkAvailable = false)
        )
    }

    @Test
    fun `group not found stops the stream`() {
        assertEquals(
            StreamRetryAction.Stop,
            classifyStreamFailure(RemoteAGNotFoundException(), isNetworkAvailable = true)
        )
    }

    @Test
    fun `invalid api key stops the stream`() {
        assertEquals(
            StreamRetryAction.Stop,
            classifyStreamFailure(InvalidApiKeyException(), isNetworkAvailable = true)
        )
    }

    @Test
    fun `SSE client error without a response backs off`() {
        assertEquals(
            StreamRetryAction.Backoff,
            classifyStreamFailure(SSEClientException(message = "boom"), isNetworkAvailable = true)
        )
    }

    @Test
    fun `backend API error backs off and IO error with no network waits`() {
        assertEquals(
            StreamRetryAction.Backoff,
            classifyStreamFailure(APIException(statusCode = 500, body = "boom"), isNetworkAvailable = true)
        )
        assertEquals(
            StreamRetryAction.AwaitNetwork,
            classifyStreamFailure(IOException("boom"), isNetworkAvailable = false)
        )
    }
}
