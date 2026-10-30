package com.tangtongda.tlauncher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * true while the TV has an internet-capable network. A launcher starts at boot, often before
 * Wi-Fi/Ethernet is up, so everything that fetches data re-runs when this turns true.
 */
fun Context.networkAvailable(): Flow<Boolean> = callbackFlow {
    val cm = getSystemService(ConnectivityManager::class.java)
    fun current(): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    trySend(current())
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(true)
        }

        override fun onLost(network: Network) {
            trySend(current())
        }
    }
    runCatching { cm.registerDefaultNetworkCallback(callback) }
    awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()
