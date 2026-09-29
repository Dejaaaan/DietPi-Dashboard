package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import okhttp3.Dns
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.ref.WeakReference
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.UnknownHostException
import java.util.Random

/**
 * Universal Smart DNS Resolver:
 * A 4-Tier Hybrid DNS provider for OkHttp that reliably resolves:
 * 1. Standard Internet domains via Android System DNS.
 * 2. Mesh VPN domains (Tailscale .ts.net, Headscale custom domains, NetBird, ZeroTier, WireGuard)
 *    by directly discovering and querying active interface DNS servers (e.g. 100.100.100.100:53),
 *    bypassing Android's "Private DNS" (DNS-over-TLS) leaks.
 * 3. Local mDNS domains (.local and single-label hostnames like 'dietpi') via RFC 6762 Multicast DNS (224.0.0.251:5353).
 * 4. Local router domains (.lan, .home.arpa, .fritz.box, .internal) via active gateway DNS servers.
 */
object UniversalSmartDns : Dns {
    private const val TAG = "UniversalSmartDns"
    private const val DNS_PORT = 53
    private const val MDNS_PORT = 5353
    private const val MDNS_MULTICAST_IPV4 = "224.0.0.251"
    private const val DEFAULT_TAILSCALE_MAGIC_DNS = "100.100.100.100"
    private const val DEFAULT_NETBIRD_DNS = "100.64.0.1"
    private const val SOCKET_TIMEOUT_MS = 2000

    private val random = Random()
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val trimmedHost = hostname.trim().trimEnd('.')
        if (trimmedHost.isEmpty()) {
            throw UnknownHostException("Hostname is empty")
        }

        // Fast-path: Check if already an IP literal (IPv4 or IPv6)
        try {
            val literal = InetAddress.getAllByName(trimmedHost)
            if (literal.isNotEmpty()) return literal.toList()
        } catch (_: Exception) {}

        // Tier 1: Standard Android System DNS
        try {
            val systemResults = Dns.SYSTEM.lookup(trimmedHost)
            if (systemResults.isNotEmpty()) {
                return systemResults
            }
        } catch (e: UnknownHostException) {
            Log.d(TAG, "Tier 1 (System DNS) failed for '$trimmedHost'. Engaging Tier 2 (Interface Discovery) & Tier 3 (mDNS)...")
        }

        // Tier 2: Dynamic Interface DNS Discovery (VPN tunnels & local gateway DNS)
        val interfaceAddresses = resolveViaInterfaceDns(trimmedHost)
        if (interfaceAddresses.isNotEmpty()) {
            Log.i(TAG, "Tier 2: Successfully resolved '$trimmedHost' via Interface DNS: $interfaceAddresses")
            return interfaceAddresses
        }

        // Tier 3: Multicast DNS (mDNS) Fallback for .local or single-label hostnames
        if (isLocalOrSingleLabel(trimmedHost)) {
            val mdnsAddresses = resolveViaMdns(trimmedHost)
            if (mdnsAddresses.isNotEmpty()) {
                Log.i(TAG, "Tier 3: Successfully resolved '$trimmedHost' via mDNS: $mdnsAddresses")
                return mdnsAddresses
            }
        }

        // Tier 4: Contextual, clear error diagnostics
        val diagnosticMsg = when {
            trimmedHost.endsWith(".ts.net", ignoreCase = true) || trimmedHost.endsWith(".ts", ignoreCase = true) ->
                "Unknown Host '$trimmedHost'. Tailscale MagicDNS could not be resolved. Ensure the Tailscale app is active and connected, or use your Pi's 100.x.y.z IP."
            trimmedHost.endsWith(".local", ignoreCase = true) || !trimmedHost.contains('.') ->
                "Unknown Host '$trimmedHost'. mDNS could not find this device on your local network. Ensure your phone is connected to the same Wi-Fi, or use the device's IP address."
            trimmedHost.endsWith(".lan", ignoreCase = true) || trimmedHost.endsWith(".home.arpa", ignoreCase = true) || trimmedHost.endsWith(".fritz.box", ignoreCase = true) ->
                "Unknown Host '$trimmedHost'. Your local router's DNS did not resolve this name. Check your connection or enter the device's IP address."
            else ->
                "Unknown Host '$trimmedHost'. DNS could not resolve this domain name. Verify the address or enter the device's IP directly."
        }

        throw UnknownHostException(diagnosticMsg)
    }

    private fun isLocalOrSingleLabel(hostname: String): Boolean {
        return hostname.endsWith(".local", ignoreCase = true) || !hostname.contains('.')
    }

    /**
     * Inspects active network interfaces (VPNs, Wi-Fi, Ethernet) to discover internal DNS servers,
     * then queries them directly via standard UDP port 53.
     */
    private fun resolveViaInterfaceDns(hostname: String): List<InetAddress> {
        val targetDnsServers = mutableSetOf<String>()

        // 1. Inspect active Network interfaces via ConnectivityManager
        try {
            val context = contextRef?.get()
            if (context != null) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (cm != null) {
                    val allNetworks = cm.allNetworks
                    for (network in allNetworks) {
                        val caps = cm.getNetworkCapabilities(network) ?: continue
                        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        val isEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

                        if (isVpn || isWifi || isEthernet) {
                            val linkProps = cm.getLinkProperties(network)
                            if (linkProps != null) {
                                for (dns in linkProps.dnsServers) {
                                    val ip = dns.hostAddress
                                    if (!ip.isNullOrBlank() && !isPublicDns(ip)) {
                                        targetDnsServers.add(ip)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting LinkProperties: ${e.message}")
        }

        // 2. Known Mesh VPN default resolvers
        // If domain ends with .ts.net, .ts or any VPN is active, add Tailscale's standard MagicDNS IP
        if (hostname.endsWith(".ts.net", ignoreCase = true) || hostname.endsWith(".ts", ignoreCase = true) || hasActiveVpn()) {
            targetDnsServers.add(DEFAULT_TAILSCALE_MAGIC_DNS)
            targetDnsServers.add(DEFAULT_NETBIRD_DNS)
        }

        // 3. Query candidate DNS servers directly
        for (dnsServerIp in targetDnsServers) {
            try {
                val result = queryDnsServer(dnsServerIp, hostname)
                if (result.isNotEmpty()) {
                    return result
                }
            } catch (_: Exception) {}
        }

        return emptyList()
    }

    private fun hasActiveVpn(): Boolean {
        return try {
            val context = contextRef?.get() ?: return false
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            cm.allNetworks.any { network ->
                val caps = cm.getNetworkCapabilities(network)
                caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isPublicDns(ip: String): Boolean {
        // Exclude common public DoT resolvers because they won't have private records
        return ip == "1.1.1.1" || ip == "1.0.0.1" ||
                ip == "8.8.8.8" || ip == "8.8.4.4" ||
                ip == "9.9.9.9" || ip == "149.112.112.112"
    }

    /**
     * Directly queries a DNS server over UDP port 53.
     */
    private fun queryDnsServer(dnsServerIp: String, hostname: String): List<InetAddress> {
        val queryId = random.nextInt(0xFFFF)
        val queryPacket = buildDnsQueryPacket(hostname, queryId)

        DatagramSocket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val targetAddr = InetAddress.getByName(dnsServerIp)
            val outPacket = DatagramPacket(queryPacket, queryPacket.size, targetAddr, DNS_PORT)
            socket.send(outPacket)

            val buffer = ByteArray(512)
            val inPacket = DatagramPacket(buffer, buffer.size)
            socket.receive(inPacket)

            return parseDnsResponse(buffer, inPacket.length, queryId)
        }
    }

    /**
     * Resolves hostnames via Multicast DNS (RFC 6762) on 224.0.0.251:5353.
     */
    private fun resolveViaMdns(hostname: String): List<InetAddress> {
        val targetName = if (hostname.endsWith(".local", ignoreCase = true)) {
            hostname
        } else {
            "$hostname.local"
        }

        val queryId = 0 // mDNS queries use ID 0
        val queryPacket = buildDnsQueryPacket(targetName, queryId)

        return try {
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val mcastGroup = InetAddress.getByName(MDNS_MULTICAST_IPV4)
                val outPacket = DatagramPacket(queryPacket, queryPacket.size, mcastGroup, MDNS_PORT)
                socket.send(outPacket)

                val buffer = ByteArray(1024)
                val inPacket = DatagramPacket(buffer, buffer.size)
                val deadline = System.currentTimeMillis() + SOCKET_TIMEOUT_MS

                while (System.currentTimeMillis() < deadline) {
                    try {
                        socket.receive(inPacket)
                        val resolved = parseDnsResponse(buffer, inPacket.length, expectedId = null)
                        if (resolved.isNotEmpty()) {
                            return resolved
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                }
                emptyList()
            }
        } catch (e: Exception) {
            Log.d(TAG, "mDNS resolution attempt for '$targetName' threw: ${e.message}")
            emptyList()
        }
    }

    private fun buildDnsQueryPacket(hostname: String, queryId: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.writeShort(queryId)
        dos.writeShort(0x0100) // Standard query, recursion desired
        dos.writeShort(1)      // Questions: 1
        dos.writeShort(0)      // Answers: 0
        dos.writeShort(0)      // NS: 0
        dos.writeShort(0)      // Additional: 0

        // Write QNAME labels
        val cleanHost = hostname.trimEnd('.')
        for (part in cleanHost.split('.')) {
            val partBytes = part.toByteArray(Charsets.US_ASCII)
            dos.writeByte(partBytes.size)
            dos.write(partBytes)
        }
        dos.writeByte(0) // Null terminator for QNAME

        dos.writeShort(1) // QTYPE: Type A (IPv4)
        dos.writeShort(1) // QCLASS: Class IN (Internet)

        dos.flush()
        return baos.toByteArray()
    }

    private fun parseDnsResponse(data: ByteArray, length: Int, expectedId: Int?): List<InetAddress> {
        val results = mutableListOf<InetAddress>()
        try {
            val bais = ByteArrayInputStream(data, 0, length)
            val dis = DataInputStream(bais)

            val id = dis.readUnsignedShort()
            if (expectedId != null && id != expectedId) return emptyList()

            val flags = dis.readUnsignedShort()
            val rcode = flags and 0x000F
            if (rcode != 0) return emptyList() // Error (e.g. NXDOMAIN)

            val qdCount = dis.readUnsignedShort()
            val anCount = dis.readUnsignedShort()
            dis.readUnsignedShort() // nsCount
            dis.readUnsignedShort() // arCount

            // Skip question section
            for (q in 0 until qdCount) {
                skipDomainName(dis)
                dis.readShort() // Type
                dis.readShort() // Class
            }

            // Read answers
            for (a in 0 until anCount) {
                skipDomainName(dis)
                val type = dis.readUnsignedShort()
                dis.readUnsignedShort() // Class
                dis.readInt()           // TTL
                val dataLen = dis.readUnsignedShort()

                if (type == 1 && dataLen == 4) { // Type A (IPv4)
                    val ip = ByteArray(4)
                    dis.readFully(ip)
                    results.add(InetAddress.getByAddress(ip))
                } else {
                    dis.skipBytes(dataLen)
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private fun skipDomainName(dis: DataInputStream) {
        while (true) {
            val len = dis.readUnsignedByte()
            if (len == 0) break
            if ((len and 0xC0) == 0xC0) {
                // Compression pointer (2 bytes total; 1 byte already read)
                dis.readByte()
                break
            } else {
                dis.skipBytes(len)
            }
        }
    }
}

/**
 * Backward compatibility alias so existing references continue to function seamlessly.
 */
val TailscaleAwareDns = UniversalSmartDns
