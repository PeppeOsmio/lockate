package com.peppeosmio.lockate.platform_service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityServiceTest {

    private val connectivityManager = mockk<ConnectivityManager>()
    private val context = mockk<Context> {
        every { getSystemService(ConnectivityManager::class.java) } returns connectivityManager
    }
    private val service = ConnectivityService(context)

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun capabilities(internet: Boolean, validated: Boolean) = mockk<NetworkCapabilities> {
        every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns internet
        every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns validated
    }

    // isNetworkAvailable

    @Test
    fun `available when active network has internet and is validated`() {
        val network = mockk<Network>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns
            capabilities(internet = true, validated = true)

        assertTrue(service.isNetworkAvailable())
    }

    @Test
    fun `unavailable when there is no active network`() {
        every { connectivityManager.activeNetwork } returns null

        assertFalse(service.isNetworkAvailable())
    }

    @Test
    fun `unavailable when capabilities are null`() {
        val network = mockk<Network>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns null

        assertFalse(service.isNetworkAvailable())
    }

    @Test
    fun `unavailable when internet capability is missing`() {
        val network = mockk<Network>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns
            capabilities(internet = false, validated = true)

        assertFalse(service.isNetworkAvailable())
    }

    @Test
    fun `unavailable when not validated`() {
        val network = mockk<Network>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns
            capabilities(internet = true, validated = false)

        assertFalse(service.isNetworkAvailable())
    }

    // awaitNetworkAvailable

    @Test
    fun `awaitNetworkAvailable returns immediately without registering a callback when already up`() =
        runTest {
            val network = mockk<Network>()
            every { connectivityManager.activeNetwork } returns network
            every { connectivityManager.getNetworkCapabilities(network) } returns
                capabilities(internet = true, validated = true)

            service.awaitNetworkAvailable()

            verify(exactly = 0) { connectivityManager.registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>()) }
        }

    @Test
    fun `awaitNetworkAvailable suspends until onAvailable, ignoring onLost, then unregisters`() =
        runTest {
            // start with no network so it registers a callback and waits
            every { connectivityManager.activeNetwork } returns null

            mockkConstructor(NetworkRequest.Builder::class)
            every { anyConstructed<NetworkRequest.Builder>().addCapability(any()) } answers
                { self as NetworkRequest.Builder }
            every { anyConstructed<NetworkRequest.Builder>().build() } returns mockk()

            val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
            every {
                connectivityManager.registerNetworkCallback(any(), capture(callbackSlot))
            } just Runs
            every { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) } just Runs

            val job = launch { service.awaitNetworkAvailable() }
            runCurrent()
            assertTrue("should be waiting on the callback", job.isActive)

            callbackSlot.captured.onLost(mockk())
            runCurrent()
            assertTrue("onLost must not complete the wait", job.isActive)

            callbackSlot.captured.onAvailable(mockk())
            runCurrent()
            assertFalse("onAvailable should complete the wait", job.isActive)

            verify { connectivityManager.unregisterNetworkCallback(callbackSlot.captured) }
        }
}
