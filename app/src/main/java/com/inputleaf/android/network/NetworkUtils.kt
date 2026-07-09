package com.inputleaf.android.network

import android.app.Application
import android.net.wifi.WifiManager
import android.util.Log
import java.net.NetworkInterface

object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Get the local IP address for network scanning.
     * Uses a prioritized approach to find the correct interface:
     * 1. Wi-Fi interfaces (wlan0, swlan0) - for regular Wi-Fi client mode
     * 2. Hotspot interfaces (ap0, swlan0) - for when phone is a hotspot
     * 3. WifiManager fallback - for older Android or edge cases
     * 4. Any private network interface - last resort
     */
    fun getLocalIpAddress(app: Application): String? {
        // Priority 1: Look for Wi-Fi/hotspot interfaces by name
        val wifiInterfaceNames = listOf("wlan0", "wlan1", "swlan0", "ap0", "eth0")
        
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            
            // Log all interfaces for debugging
            interfaces.forEach { iface ->
                if (iface.isUp && !iface.isLoopback) {
                    val addrs = iface.inetAddresses.toList()
                        .filter { it.hostAddress?.contains('.') == true }
                        .mapNotNull { it.hostAddress }
                    if (addrs.isNotEmpty()) {
                        Log.d(TAG, "Interface ${iface.name}: $addrs")
                    }
                }
            }
            
            // First pass: Look for preferred Wi-Fi interfaces
            for (ifaceName in wifiInterfaceNames) {
                val iface = interfaces.find { it.name == ifaceName && it.isUp && !it.isLoopback }
                if (iface != null) {
                    val ip = getIPv4FromInterface(iface)
                    if (ip != null) {
                        Log.d(TAG, "Using preferred interface ${iface.name}: $ip")
                        return ip
                    }
                }
            }
            
            // Second pass: WifiManager fallback (works for regular Wi-Fi)
            val wifiIp = getWifiManagerIp(app)
            if (wifiIp != null && wifiIp != "0.0.0.0") {
                Log.d(TAG, "Using WifiManager IP: $wifiIp")
                return wifiIp
            }
            
            // Third pass: Any private network interface (except cellular)
            val cellularPrefixes = listOf("rmnet", "ccmni", "pdp", "ppp", "uwbr")
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                // Skip cellular interfaces
                if (cellularPrefixes.any { iface.name.startsWith(it) }) continue
                
                val ip = getIPv4FromInterface(iface)
                if (ip != null && isPrivateIP(ip)) {
                    Log.d(TAG, "Using fallback interface ${iface.name}: $ip")
                    return ip
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address: ${e.message}", e)
        }
        
        Log.e(TAG, "Could not determine local IP address")
        return null
    }
    
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
    
    private fun isPrivateIP(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return when {
            first == 10 -> true  // 10.0.0.0/8
            first == 172 && second in 16..31 -> true  // 172.16.0.0/12
            first == 192 && second == 168 -> true  // 192.168.0.0/16
            else -> false
        }
    }
}
