package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkConnectivityMonitorTest {

    @Test
    fun onLost_clearsConnectivityEvenIfActiveNetworkHasNotUpdatedYet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = shadowOf(cm)

        val network: Network = ShadowNetwork.newInstance(42)
        val caps: NetworkCapabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowCm.setNetworkCapabilities(network, caps)
        shadowCm.setDefaultNetworkActive(true)

        val monitor = NetworkConnectivityMonitor(cm, registerCallback = false)
        monitor.networkCallback.onAvailable(network)
        assertTrue(monitor.isConnected.value)

        // Simulate OS race: onLost fires before ConnectivityManager clears activeNetwork
        monitor.networkCallback.onLost(network)
        assertFalse(monitor.isConnected.value)

        // Reconnect restores connectivity
        val network2: Network = ShadowNetwork.newInstance(43)
        shadowCm.setNetworkCapabilities(network2, caps)
        monitor.networkCallback.onAvailable(network2)
        assertTrue(monitor.isConnected.value)

        monitor.dispose()
    }
}
