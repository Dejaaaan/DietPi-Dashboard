package com.example.data.discovery

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class DiscoveredDietPi(
    val host: String,
    val port: Int = 5252,
    val name: String = "Server Node",
    val discoveryMethod: String = "Auto-Discovered",
    val useHttps: Boolean = false,
    val isDashboardReady: Boolean = true,
    val resolvedIp: String? = null,
    val isConfirmedDietPi: Boolean = false
)

class NetworkDiscoveryManager(private val context: Context) {

    private val TAG = "NetworkDiscovery"

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredNodes = MutableStateFlow<List<DiscoveredDietPi>>(emptyList())
    val discoveredNodes: StateFlow<List<DiscoveredDietPi>> = _discoveredNodes.asStateFlow()

    private val _scanStatus = MutableStateFlow("Idle")
    val scanStatus: StateFlow<String> = _scanStatus.asStateFlow()

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private var activeDiscoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var multicastLock: WifiManager.MulticastLock? = null
    private var scanJob: Job? = null

    // Lenient OkHttp client that trusts self-signed certs (standard on local DietPi-Dashboard)
    private val probeClient: OkHttpClient by lazy {
        createLenientOkHttpClient()
    }

    private fun createLenientOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(2000, TimeUnit.MILLISECONDS)
            .readTimeout(2000, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        try {
            val trustAllCerts = arrayOf<TrustManager>(@SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (_: Exception) {}

        return builder.build()
    }

    fun startDiscovery(coroutineScope: CoroutineScope, customSubnet: String? = null) {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredNodes.value = emptyList()
        _scanStatus.value = "Starting discovery..."

        scanJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                // 1. Acquire Wi-Fi Multicast Lock and start mDNS
                acquireMulticastLock()
                startNsdDiscovery()

                // 2. Parallel hostname resolution (immediate fast check)
                _scanStatus.value = "Resolving hostnames (dietpi.local, emulator)..."
                val knownHosts = listOf(
                    "dietpi.local" to "DietPi (.local)",
                    "dietpi" to "DietPi (local domain)",
                    "dietpi.lan" to "DietPi (.lan)",
                    "dietpi.home" to "DietPi (.home)",
                    "dietpi.fritz.box" to "DietPi (.fritz.box)",
                    "10.0.2.2" to "DietPi (Host loopback)"
                )

                knownHosts.forEach { (host, label) ->
                    launch {
                        checkHostFast(host, label)
                    }
                }

                // 3. Subnet scanning
                val subnetsToScan = if (!customSubnet.isNullOrBlank()) {
                    listOf(customSubnet.trim().trimEnd('.'))
                } else {
                    getCandidateSubnets()
                }

                if (subnetsToScan.isNotEmpty()) {
                    for (subnet in subnetsToScan) {
                        if (!isActive) break
                        _scanStatus.value = "Scanning subnet $subnet.x..."
                        scanSubnet(subnet)
                    }
                } else {
                    _scanStatus.value = "No local subnet found; checking default 192.168.1.x..."
                    scanSubnet("192.168.1")
                }

                // Give mDNS services a short window to resolve
                delay(1200)

                val count = _discoveredNodes.value.size
                _scanStatus.value = if (count > 0) "Found $count DietPi node${if (count == 1) "" else "s"}" else "Scan finished: no DietPi nodes found"

            } catch (e: Exception) {
                Log.w(TAG, "Discovery scan exception: ${e.message}")
                _scanStatus.value = "Scan error: ${e.message}"
            } finally {
                stopNsdDiscovery()
                releaseMulticastLock()
                _isScanning.value = false
            }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
        stopNsdDiscovery()
        releaseMulticastLock()
        _isScanning.value = false
        _scanStatus.value = "Scan stopped"
    }

    /**
     * Scans a /24 subnet (1..254) with a concurrency limiter using lightweight TCP socket probes
     */
    private suspend fun scanSubnet(subnetPrefix: String) = coroutineScope {
        val semaphore = Semaphore(36) // limit concurrent sockets to avoid thread/socket starvation
        val scannedCount = AtomicInteger(0)

        val jobs = (1..254).map { hostNum ->
            val ip = "$subnetPrefix.$hostNum"
            launch {
                semaphore.withPermit {
                    val count = scannedCount.incrementAndGet()
                    if (count % 30 == 0) {
                        _scanStatus.value = "Scanning $subnetPrefix.x ($count/254)..."
                    }
                    probeIpCandidate(ip)
                }
            }
        }
        jobs.joinAll()
    }

    /**
     * Probes an IP on port 5252 (official DietPi Dashboard port).
     * If port 5252 is open, tests HTTPS/HTTP for Dashboard responsiveness
     * and checks whether the response is verified DietPi or another service.
     */
    private fun probeIpCandidate(ip: String) {
        try {
            // Fast socket test for DietPi Dashboard default port (5252)
            if (!isPortOpen(ip, 5252, timeoutMs = 300)) {
                return
            }

            // Test HTTPS first on port 5252
            val httpsConfirmed = testDashboardHttp(ip, 5252, useHttps = true)
            if (httpsConfirmed != null) {
                addDiscoveredNode(
                    DiscoveredDietPi(
                        host = ip,
                        port = 5252,
                        name = if (httpsConfirmed) "DietPi ($ip)" else "Server ($ip)",
                        discoveryMethod = "Port 5252 (HTTPS)",
                        useHttps = true,
                        isDashboardReady = true,
                        resolvedIp = ip,
                        isConfirmedDietPi = httpsConfirmed
                    )
                )
                return
            }

            // Test HTTP on port 5252
            val httpConfirmed = testDashboardHttp(ip, 5252, useHttps = false)
            if (httpConfirmed != null) {
                addDiscoveredNode(
                    DiscoveredDietPi(
                        host = ip,
                        port = 5252,
                        name = if (httpConfirmed) "DietPi ($ip)" else "Server ($ip)",
                        discoveryMethod = "Port 5252 (HTTP)",
                        useHttps = false,
                        isDashboardReady = true,
                        resolvedIp = ip,
                        isConfirmedDietPi = httpConfirmed
                    )
                )
            }
        } catch (_: Exception) {}
    }

    /**
     * Checks a hostname (e.g. dietpi.local) with DNS resolution and port probe
     */
    private fun checkHostFast(host: String, label: String) {
        try {
            val addr = InetAddress.getByName(host)
            val ip = addr.hostAddress ?: host

            // Test port 5252
            if (isPortOpen(host, 5252, 600) || isPortOpen(ip, 5252, 600)) {
                val httpsConfirmed = testDashboardHttp(host, 5252, useHttps = true)
                    ?: testDashboardHttp(ip, 5252, useHttps = true)

                if (httpsConfirmed != null) {
                    addDiscoveredNode(
                        DiscoveredDietPi(
                            host = host,
                            port = 5252,
                            name = if (httpsConfirmed) label else host,
                            discoveryMethod = "DNS Hostname (HTTPS)",
                            useHttps = true,
                            isDashboardReady = true,
                            resolvedIp = ip,
                            isConfirmedDietPi = httpsConfirmed
                        )
                    )
                    return
                }

                val httpConfirmed = testDashboardHttp(host, 5252, useHttps = false)
                    ?: testDashboardHttp(ip, 5252, useHttps = false)

                if (httpConfirmed != null) {
                    addDiscoveredNode(
                        DiscoveredDietPi(
                            host = host,
                            port = 5252,
                            name = if (httpConfirmed) label else host,
                            discoveryMethod = "DNS Hostname (HTTP)",
                            useHttps = false,
                            isDashboardReady = true,
                            resolvedIp = ip,
                            isConfirmedDietPi = httpConfirmed
                        )
                    )
                    return
                }
            }

            // Only check SSH port 22 if the hostname explicitly contains "dietpi" (e.g. dietpi.local)
            // to notify the user if DietPi OS is present but the dashboard is not yet installed.
            if (host.contains("dietpi", ignoreCase = true) && (isPortOpen(host, 22, 500) || isPortOpen(ip, 22, 500))) {
                addDiscoveredNode(
                    DiscoveredDietPi(
                        host = host,
                        port = 5252,
                        name = "$label (No Dashboard)",
                        discoveryMethod = "SSH Port 22 (Dashboard not installed)",
                        useHttps = false,
                        isDashboardReady = false,
                        resolvedIp = ip,
                        isConfirmedDietPi = true
                    )
                )
            }
        } catch (_: Exception) {}
    }

    /**
     * Lightweight TCP connect check
     */
    private fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if endpoint responds with DietPi Dashboard headers, title, or JSON status.
     * Returns true if confirmed DietPi, false if HTTP responded on port but unconfirmed DietPi, or null if unreachable.
     */
    private fun testDashboardHttp(host: String, port: Int, useHttps: Boolean): Boolean? {
        val scheme = if (useHttps) "https" else "http"
        val testPaths = listOf("/", "/login", "/system")

        for (path in testPaths) {
            try {
                val request = Request.Builder()
                    .url("$scheme://$host:$port$path")
                    .header("User-Agent", "DietPi-Companion-Android")
                    .build()

                probeClient.newCall(request).execute().use { response ->
                    val code = response.code
                    if (code in 200..499) {
                        val body = try { response.peekBody(1024 * 16).string() } catch (_: Exception) { "" }
                        val serverHeader = response.header("Server") ?: ""
                        val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1) ?: ""

                        val isConfirmedDietPi = body.contains("DietPi", ignoreCase = true) ||
                                serverHeader.contains("DietPi", ignoreCase = true) ||
                                title.contains("DietPi", ignoreCase = true) ||
                                body.contains("dietpi-dashboard", ignoreCase = true)

                        return isConfirmedDietPi
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    @Synchronized
    private fun addDiscoveredNode(node: DiscoveredDietPi) {
        val current = _discoveredNodes.value.toMutableList()
        val existingIndex = current.indexOfFirst { existing ->
            existing.host.equals(node.host, ignoreCase = true) ||
            (existing.resolvedIp != null && existing.resolvedIp.equals(node.resolvedIp, ignoreCase = true)) ||
            (existing.resolvedIp != null && existing.resolvedIp.equals(node.host, ignoreCase = true)) ||
            (node.resolvedIp != null && existing.host.equals(node.resolvedIp, ignoreCase = true))
        }

        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            // Prefer friendly hostname (e.g. dietpi.local) over raw IP if available
            val isExistingIp = existing.host.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))
            val isNewIp = node.host.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))

            val preferredHost = if (isExistingIp && !isNewIp) node.host else existing.host
            val preferredIp = node.resolvedIp ?: existing.resolvedIp
            val preferredName = if (node.isConfirmedDietPi && !existing.isConfirmedDietPi) node.name else existing.name
            val preferredMethod = if (node.isDashboardReady && !existing.isDashboardReady) node.discoveryMethod else existing.discoveryMethod

            current[existingIndex] = existing.copy(
                host = preferredHost,
                resolvedIp = preferredIp,
                port = if (node.isDashboardReady) node.port else existing.port,
                name = preferredName,
                discoveryMethod = preferredMethod,
                useHttps = existing.useHttps || node.useHttps,
                isDashboardReady = existing.isDashboardReady || node.isDashboardReady,
                isConfirmedDietPi = existing.isConfirmedDietPi || node.isConfirmedDietPi
            )
            _discoveredNodes.value = current
        } else {
            current.add(node)
            _discoveredNodes.value = current
            _scanStatus.value = "Discovered: ${node.name} (${node.host})"
        }
    }

    /**
     * Starts mDNS / NSD listeners for _http._tcp. and _dietpi-dashboard._tcp.
     */
    private fun startNsdDiscovery() {
        val serviceTypes = listOf("_http._tcp.", "_dietpi-dashboard._tcp.")
        for (serviceType in serviceTypes) {
            try {
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(st: String?, errorCode: Int) {
                        Log.w(TAG, "NSD onStartDiscoveryFailed for $st: $errorCode")
                    }

                    override fun onStopDiscoveryFailed(st: String?, errorCode: Int) {
                        Log.w(TAG, "NSD onStopDiscoveryFailed: $errorCode")
                    }

                    override fun onDiscoveryStarted(st: String?) {
                        Log.d(TAG, "NSD Discovery started for $st")
                    }

                    override fun onDiscoveryStopped(st: String?) {
                        Log.d(TAG, "NSD Discovery stopped for $st")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                        if (serviceInfo == null) return
                        val name = serviceInfo.serviceName ?: ""
                        val isDietPiCandidate = name.contains("dietpi", ignoreCase = true) ||
                                name.contains("dashboard", ignoreCase = true)

                        if (isDietPiCandidate) {
                            resolveNsdServiceSafely(serviceInfo)
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
                }

                activeDiscoveryListeners.add(listener)
                nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error initiating NSD for $serviceType: ${e.message}")
            }
        }
    }

    private fun resolveNsdServiceSafely(serviceInfo: NsdServiceInfo) {
        try {
            // First, attempt direct resolution of serviceName.local via DNS
            val hostCandidate = "${serviceInfo.serviceName}.local"
            try {
                val addr = InetAddress.getByName(hostCandidate)
                val ip = addr.hostAddress
                if (!ip.isNullOrBlank()) {
                    checkHostFast(ip, serviceInfo.serviceName)
                    return
                }
            } catch (_: Exception) {}

            // Otherwise invoke NsdManager resolver
            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(si: NsdServiceInfo?, errorCode: Int) {}
                override fun onServiceResolved(resolved: NsdServiceInfo?) {
                    val host = resolved?.host?.hostAddress
                    val port = resolved?.port ?: 5252
                    val name = resolved?.serviceName ?: "DietPi mDNS"

                    if (!host.isNullOrBlank()) {
                        checkHostFast(host, name)
                    }
                }
            })
        } catch (_: Exception) {}
    }

    private fun stopNsdDiscovery() {
        for (listener in activeDiscoveryListeners) {
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {}
        }
        activeDiscoveryListeners.clear()
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("DietPiMulticastLock")?.apply {
                    setReferenceCounted(true)
                }
            }
            multicastLock?.acquire()
        } catch (e: Exception) {
            Log.w(TAG, "Failed acquiring MulticastLock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }

    /**
     * Detects candidate IPv4 subnets across active Wi-Fi and Ethernet interfaces
     */
    fun getCandidateSubnets(): List<String> {
        val subnets = LinkedHashSet<String>()

        // 1. Try WifiManager IP directly
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ip = String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                val parts = ip.split(".")
                if (parts.size == 4 && parts[0] != "0") {
                    subnets.add("${parts[0]}.${parts[1]}.${parts[2]}")
                }
            }
        } catch (_: Exception) {}

        // 2. Iterate NetworkInterfaces, prioritizing wlan and eth
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            val sorted = interfaces.sortedByDescending { iface ->
                val name = iface.name.lowercase()
                when {
                    name.startsWith("wlan") -> 3
                    name.startsWith("eth") || name.startsWith("en") -> 2
                    else -> 1
                }
            }

            for (iface in sorted) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress ?: continue
                        val parts = hostAddress.split(".")
                        if (parts.size == 4) {
                            val first = parts[0].toIntOrNull() ?: 0
                            val second = parts[1].toIntOrNull() ?: 0
                            // Private IPv4 ranges: 192.168.x.x, 10.x.x.x, 172.16..31.x.x
                            val isPrivate = (first == 192 && second == 168) ||
                                    (first == 10) ||
                                    (first == 172 && second in 16..31)
                            if (isPrivate) {
                                subnets.add("${parts[0]}.${parts[1]}.${parts[2]}")
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Common fallback subnets if none detected
        if (subnets.isEmpty()) {
            subnets.add("192.168.1")
            subnets.add("192.168.0")
        }

        return subnets.toList()
    }
}
