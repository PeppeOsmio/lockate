package com.peppeosmio.lockate.utils

import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.NoRouteToHostException

class ErrorInfoTest {

    @Test
    fun `isConnectionError is true for ConnectException`() {
        assertTrue(ErrorInfo.fromException(ConnectException()).isConnectionError)
    }

    @Test
    fun `isConnectionError is true for NoRouteToHostException`() {
        assertTrue(ErrorInfo.fromException(NoRouteToHostException()).isConnectionError)
    }

    @Test
    fun `isConnectionError is false for other exceptions`() {
        assertFalse(ErrorInfo.fromException(RemoteAGNotFoundException()).isConnectionError)
        assertFalse(ErrorInfo.fromException(IllegalStateException("x")).isConnectionError)
    }
}
