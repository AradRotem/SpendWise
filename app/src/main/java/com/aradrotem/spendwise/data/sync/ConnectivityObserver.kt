package com.aradrotem.spendwise.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Emits true whenever the device regains internet connectivity - one of SyncEngine's trigger
// points, alongside app resume and a manual "Sync now". No new library needed: plain
// ConnectivityManager.NetworkCallback wrapped as a Flow.
class ConnectivityObserver(context: Context) {
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    val onConnected: Flow<Unit> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
}
