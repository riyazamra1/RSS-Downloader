package com.riyaz.rssdownloader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Network gate for download-job creation.
 *
 * wifiOnly=false (the app default) permits both Wi-Fi and cellular data.
 * wifiOnly=true permits only a validated Wi-Fi connection.
 * Call [check] immediately before submitting a new download job.
 */
object DownloadNetworkPolicy {
    sealed class Decision {
        data object Allowed : Decision()
        data object NoConnection : Decision()
        data object WifiRequired : Decision()
    }

    fun check(context: Context, wifiOnly: Boolean): Decision {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return Decision.NoConnection
        val network = manager.activeNetwork ?: return Decision.NoConnection
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return Decision.NoConnection

        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) return Decision.NoConnection

        val onWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val onCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        return when {
            wifiOnly && !onWifi -> Decision.WifiRequired
            onWifi || onCellular -> Decision.Allowed
            else -> Decision.NoConnection
        }
    }
}
