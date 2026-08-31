package com.ivarna.fluxlinux.core.terminal

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Carries Android's active-network DNS servers into the guest namespace.
 *
 * The app process can see Android's validated resolver even when a PRoot guest
 * cannot use the resolver inherited from a stale rootfs. Values are encoded as
 * a comma-separated list because the resulting environment variable is passed
 * through several POSIX shells before the guest setup script consumes it.
 */
object GuestDnsConfigurator {
    const val ENV_NAME = "FLUX_DNS_SERVERS"
    private const val TAG = "GuestDnsConfigurator"
    private const val SEPARATOR = ","

    /** Final fallback only when Android cannot provide an active resolver. */
    val PUBLIC_FALLBACK: List<String> = listOf("8.8.8.8", "1.1.1.1", "8.8.4.4")

    /** Return valid numeric resolver addresses from Android LinkProperties. */
    fun fromLinkProperties(linkProperties: LinkProperties?): List<String> =
        linkProperties?.dnsServers.orEmpty().mapNotNull { normalize(it.hostAddress) }.distinct()

    /** Resolve the active Android network, falling back only when necessary. */
    fun resolve(context: Context): List<String> {
        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork
        val linkProperties = network?.let { manager.getLinkProperties(it) }
        val androidServers = fromLinkProperties(linkProperties)
        if (androidServers.isNotEmpty()) {
            Log.i(TAG, "activeNetwork=$network dnsServers=${androidServers.joinToString()}")
            return androidServers
        }
        Log.w(TAG, "Android active-network DNS unavailable; using final public fallback")
        return PUBLIC_FALLBACK
    }

    /** The environment representation consumed by flux_guest_common.sh. */
    fun environmentValue(context: Context): String = encode(resolve(context))

    fun encode(servers: Iterable<String>): String =
        servers.mapNotNull(::normalize).distinct().joinToString(SEPARATOR)

    fun decode(value: String?): List<String> =
        value.orEmpty().split(SEPARATOR).mapNotNull(::normalize).distinct()

    private fun normalize(raw: String?): String? {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.any(Char::isWhitespace)) return null
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length - 1)
        }
        // LinkProperties may include an IPv6 interface scope. resolv.conf
        // expects the numeric address, not the Android interface suffix.
        value = value.substringBefore('%')
        if (value.isEmpty()) return null
        val numericShape = if (value.contains(':')) {
            value.all { it in "0123456789abcdefABCDEF:" }
        } else {
            value.all { it.isDigit() || it == '.' }
        }
        if (!numericShape) return null
        return try {
            val address = InetAddress.getByName(value)
            when {
                address is Inet4Address && isValidIpv4(value) -> address.hostAddress
                value.contains(':') -> address.hostAddress?.substringBefore('%')
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidIpv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
