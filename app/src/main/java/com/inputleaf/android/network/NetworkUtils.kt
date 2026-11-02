package com.inputleaf.android.network

import android.app.Application
import android.net.wifi.WifiManager
import android.util.Log
import java.net.NetworkInterface

object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /** Wi-Fi and hotspot interface names, in scan order. */
    private val WIFI_INTERFACE_NAMES = listOf("wlan0", "wlan1", "swlan0", "ap0", "eth0")
    private val CELLULAR_PREFIXES = listOf("rmnet", "ccmni", "pdp", "ppp", "uwbr")

    /**
     * One local IPv4 address per network to scan. A phone can be on Wi-Fi and host a
     * hotspot at the same time (wlan0 + wlan1 on OnePlus), and the server may only be
     * reachable on the hotspot, so every network is returned, not just the first.
     */
    fun getLocalIpAddresses(app: Application): List<String> {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            val candidates = interfaces
                .filter { it.isUp && !it.isLoopback }
                .map { iface -> iface.name to listOfNotNull(getIPv4FromInterface(iface)) }
            candidates.filter { it.second.isNotEmpty() }
                .forEach { (name, addrs) -> Log.d(TAG, "Interface $name: $addrs") }
            val ips = scanAddresses(candidates)
            if (ips.isNotEmpty()) {
                Log.d(TAG, "Scanning from $ips")
                return ips
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address: ${e.message}", e)
        }

        // WifiManager fallback for older Android or edge cases
        val wifiIp = getWifiManagerIp(app)
        if (wifiIp != null && wifiIp != "0.0.0.0") {
            Log.d(TAG, "Using WifiManager IP: $wifiIp")
            return listOf(wifiIp)
        }
        Log.e(TAG, "Could not determine local IP address")
        return emptyList()
    }

    /**
     * Picks the addresses to scan from (interface name, IPv4 addresses) pairs: known
     * Wi-Fi/hotspot interfaces first and with any address, other non-cellular
     * interfaces only with a private one. One address per /24, since that's what
     * [ServerScanner] sweeps.
     */
    internal fun scanAddresses(interfaces: List<Pair<String, List<String>>>): List<String> =
        interfaces
            .filterNot { (name, _) -> CELLULAR_PREFIXES.any { name.startsWith(it) } }
            .sortedBy { (name, _) -> WIFI_INTERFACE_NAMES.indexOf(name).let { if (it < 0) Int.MAX_VALUE else it } }
            .flatMap { (name, addrs) -> addrs.filter { name in WIFI_INTERFACE_NAMES || isPrivateIP(it) } }
            .distinctBy { it.substringBeforeLast('.') }

    private fun getIPv4FromInterface(iface: NetworkInterface): String? {
        val addresses = iface.inetAddresses
        while (addresses.hasMoreElements()) {
            val address = addresses.nextElement()
            if (!address.isLoopbackAddress) {
                val hostAddress = address.hostAddress
                // Must be IPv4 (contains dots, no colons for IPv6)
                if (hostAddress != null && hostAddress.contains('.') && !hostAddress.contains(':')) {
                    // Strip any zone ID suffix (e.g., %wlan0)
                    return hostAddress.substringBefore('%')
                }
            }
        }
        return null
    }
    
    @Suppress("DEPRECATION")
    private fun getWifiManagerIp(app: Application): String? {
        return try {
            val wm = app.getSystemService(WifiManager::class.java)
            val ipInt = wm.connectionInfo.ipAddress
            if (ipInt == 0) return null
            "${ipInt and 0xFF}.${(ipInt shr 8) and 0xFF}.${(ipInt shr 16) and 0xFF}.${(ipInt shr 24) and 0xFF}"
        } catch (e: Exception) {
            Log.w(TAG, "WifiManager fallback failed: ${e.message}")
            null
        }
    }
    
    internal fun isPrivateIP(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        val third = parts[2].toIntOrNull() ?: return false
        val fourth = parts[3].toIntOrNull() ?: return false
        if (first !in 0..255 || second !in 0..255 || third !in 0..255 || fourth !in 0..255) return false
        return when {
            first == 10 -> true  // 10.0.0.0/8
            first == 172 && second in 16..31 -> true  // 172.16.0.0/12
            first == 192 && second == 168 -> true  // 192.168.0.0/16
            else -> false
        }
    }
}
