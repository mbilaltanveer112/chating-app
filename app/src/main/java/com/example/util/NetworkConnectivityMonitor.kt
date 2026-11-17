package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class NetworkConnectivityMonitor internal constructor(
    private val connectivityManager: ConnectivityManager?,
    registerCallback: Boolean
) {
    constructor(context: Context) : this(
        connectivityManager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager,
        registerCallback = true
    )

    private val validatedNetworks = ConcurrentHashMap.newKeySet<Network>()
    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var callbackRegistered = false

    internal val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            validatedNetworks.add(network)
            updateState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (hasInternet) {
                validatedNetworks.add(network)
            } else {
                validatedNetworks.remove(network)
            }
            updateState()
        }

        override fun onLost(network: Network) {
            validatedNetworks.remove(network)
            updateState(lostNetwork = network)
        }

        override fun onUnavailable() {
            validatedNetworks.clear()
            updateState()
        }
    }

    init {
        val cm = connectivityManager
        if (registerCallback && cm != null) {
            runCatching {
                cm.registerDefaultNetworkCallback(networkCallback)
                callbackRegistered = true
            }
        }
    }

    private fun checkCurrentConnectivity(excludedNetwork: Network? = null): Boolean {
        val cm = connectivityManager ?: return true
        return runCatching {
            val active = cm.activeNetwork ?: return@runCatching false
            if (excludedNetwork != null && active == excludedNetwork) {
                return@runCatching false
            }
            val caps = cm.getNetworkCapabilities(active) ?: return@runCatching false
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (hasInternet) {
                validatedNetworks.add(active)
            }
            hasInternet
        }.getOrDefault(false)
    }

    private fun updateState(lostNetwork: Network? = null) {
        _isConnected.value = validatedNetworks.isNotEmpty() ||
            checkCurrentConnectivity(excludedNetwork = lostNetwork)
    }

    fun dispose() {
        val cm = connectivityManager ?: return
        if (callbackRegistered) {
            callbackRegistered = false
            runCatching { cm.unregisterNetworkCallback(networkCallback) }
        }
    }
}
