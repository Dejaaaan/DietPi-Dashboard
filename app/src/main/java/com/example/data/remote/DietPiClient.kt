package com.example.data.remote

import android.annotation.SuppressLint
import com.example.data.local.ServerEntity
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class DietPiClient(val server: ServerEntity) {

    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (newCookie in cookies) {
                cookieStore.removeAll { it.name == newCookie.name }
                cookieStore.add(newCookie)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore.toList()
        }

        fun hasToken(): Boolean = synchronized(this) {
            cookieStore.any { it.name == "token" && it.value.isNotEmpty() }
        }

        fun clear() = synchronized(this) {
            cookieStore.clear()
        }
    }

    fun getCookies(): List<Cookie> {
        val httpUrl = server.baseUrl.toHttpUrlOrNull() ?: return emptyList()
        return cookieJar.loadForRequest(httpUrl)
    }

    private val okHttpClient: OkHttpClient = createOkHttpClient()

    private fun createOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        // Redirect Interceptor: If DietPi-Dashboard redirects to "localhost" or "127.0.0.1" (as seen in HTTP 301),
        // rewrite the Location header so that the client on the phone connects to server.host instead of itself!
        builder.addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.isRedirect) {
                val locationHeader = response.header("Location")
                if (locationHeader != null) {
                    try {
                        val parsed = locationHeader.toHttpUrlOrNull()
                        if (parsed != null && (parsed.host == "localhost" || parsed.host == "127.0.0.1")) {
                            response.close()
                            val fixedUrl = parsed.newBuilder()
                                .host(server.host)
                                .port(if (parsed.port != -1) parsed.port else server.port)
                                .build()
                            val redirectRequest = request.newBuilder()
                                .url(fixedUrl)
                                .build()
                            return@addInterceptor chain.proceed(redirectRequest)
                        }
                    } catch (_: Exception) {}
                }
            }
            response
        }

        // Local DietPi servers frequently use self-signed TLS certificates (/opt/dietpi-dashboard/cert.pem).
        // Configure lenient trust so both direct HTTPS and 301/302 redirects to HTTPS succeed without SSL errors.
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
        } catch (_: Exception) {
            // Fallback to standard SSL
        }

        return builder.build()
    }

    private fun computeSha512(input: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-512")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun login(): Result<Boolean> = withContext(Dispatchers.IO) {
        val passwordToTry = server.password

        // 1. Try standard plain password form field: pass (as expected by DietPi-Dashboard LoginForm)
        val form1 = FormBody.Builder()
            .add("pass", passwordToTry)
            .build()
        val req1 = Request.Builder()
            .url("${server.baseUrl}/login")
            .post(form1)
            .build()

        try {
            val resp1 = okHttpClient.newCall(req1).execute()
            val finalPath1 = resp1.request.url.encodedPath
            val success1 = cookieJar.hasToken() || (!finalPath1.contains("login") && (resp1.isSuccessful || resp1.code in 300..399))
            resp1.close()
            if (success1) {
                return@withContext Result.success(true)
            }
        } catch (_: Exception) {}

        if (passwordToTry.isNotEmpty()) {
            // 2. Try with pre-computed SHA-512 in "pass" field
            val sha = computeSha512(passwordToTry)
            if (sha.isNotEmpty()) {
                val form2 = FormBody.Builder()
                    .add("pass", sha)
                    .build()
                val req2 = Request.Builder()
                    .url("${server.baseUrl}/login")
                    .post(form2)
                    .build()
                try {
                    val resp2 = okHttpClient.newCall(req2).execute()
                    val finalPath2 = resp2.request.url.encodedPath
                    val success2 = cookieJar.hasToken() || (!finalPath2.contains("login") && (resp2.isSuccessful || resp2.code in 300..399))
                    resp2.close()
                    if (success2) {
                        return@withContext Result.success(true)
                    }
                } catch (_: Exception) {}
            }

            // 3. Try with "password" parameter name for standard reverse proxies
            val form3 = FormBody.Builder()
                .add("password", passwordToTry)
                .build()
            val req3 = Request.Builder()
                .url("${server.baseUrl}/login")
                .post(form3)
                .build()
            try {
                val resp3 = okHttpClient.newCall(req3).execute()
                val finalPath3 = resp3.request.url.encodedPath
                val success3 = cookieJar.hasToken() || (!finalPath3.contains("login") && (resp3.isSuccessful || resp3.code in 300..399))
                resp3.close()
                if (success3) {
                    return@withContext Result.success(true)
                }
            } catch (_: Exception) {}
        }

        val errMsg = if (passwordToTry.isEmpty()) {
            "Authentication required for '${server.nickname}'. Please edit server to enter the password."
        } else {
            "Authentication failed for '${server.nickname}'. Check that the password is correct."
        }
        Result.failure(Exception(errMsg))
    }

    suspend fun ensureAuthenticated(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (cookieJar.hasToken()) {
            return@withContext Result.success(true)
        }
        login()
    }

    private suspend fun executeAuthenticated(requestBuilder: () -> Request): Response = withContext(Dispatchers.IO) {
        if (!cookieJar.hasToken()) {
            login()
        }

        var resp = okHttpClient.newCall(requestBuilder()).execute()

        // Check if redirected to login page or received auth challenge
        val finalPath = resp.request.url.encodedPath
        val isAuthRedirect = finalPath.contains("login") || resp.code in listOf(401, 403)
        var needsReauth = isAuthRedirect

        if (!needsReauth && resp.isSuccessful) {
            val preview = try { resp.peekBody(1024).string() } catch (_: Exception) { "" }
            if (preview.contains("Login Form") || preview.contains("name=\"pass\"")) {
                needsReauth = true
            }
        }

        if (needsReauth) {
            resp.close()
            cookieJar.clear()
            val loginRes = login()
            if (loginRes.isSuccess) {
                // Retry request with authenticated session cookie
                resp = okHttpClient.newCall(requestBuilder()).execute()
            }
        }

        resp
    }

    suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            // 1. Probe /system directly to verify reachability and check auth
            val request = Request.Builder()
                .url("${server.baseUrl}/system")
                .header("User-Agent", "DietPi-Android-Dashboard/1.0")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - start

            val finalPath = response.request.url.encodedPath
            val preview = try { response.peekBody(1024).string() } catch (_: Exception) { "" }
            val isLoginPage = finalPath.contains("login") || preview.contains("Login Form") || response.code in listOf(401, 403)
            response.close()

            if (isLoginPage) {
                val loginRes = login()
                if (loginRes.isFailure) {
                    return@withContext Result.failure(
                        Exception("Authentication Required: Password for '${server.nickname}' is incorrect or missing. Edit server to set password.")
                    )
                }
            }

            Result.success(latency)
        } catch (e: java.net.ConnectException) {
            Result.failure(Exception("Connection Refused (${server.host}:${server.port}). DietPi-Dashboard may not be running, or port ${server.port} is blocked by firewall."))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Connection Timed Out connecting to ${server.baseUrl}. Verify that your phone is on the same Wi-Fi subnet (192.168.1.x), that 'Use HTTPS' is enabled, and port ${server.port} is reachable."))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("Unknown Host '${server.host}'. DNS could not resolve this domain. Use your Pi's IP address (192.168.1.39)."))
        } catch (e: javax.net.ssl.SSLException) {
            Result.failure(Exception("SSL/TLS Handshake Failed for ${server.baseUrl}. Ensure self-signed certificates are accepted or toggle HTTPS."))
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    suspend fun fetchSystemStats(): Result<SystemStats> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/system")
                    .get()
                    .build()
            }

            if (!response.isSuccessful) {
                response.close()
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val html = response.body?.string() ?: ""
            response.close()
            val doc = Jsoup.parse(html)

            val swapDiv = doc.selectFirst("#system-swap")
            val dataCpu = parseFloats(swapDiv?.attr("data-cpu"))
            val dataRam = parseFloats(swapDiv?.attr("data-ram"))
            val dataSwap = parseFloats(swapDiv?.attr("data-swap"))
            val dataTemp = parseFloats(swapDiv?.attr("data-temp"))
            val dataSent = parseFloats(swapDiv?.attr("data-sent"))
            val dataRecv = parseFloats(swapDiv?.attr("data-recv"))

            var globalCpu = dataCpu.firstOrNull() ?: 0f
            var cpuTemp: Float? = dataTemp.firstOrNull()
            val cores = mutableListOf<Float>()
            val temperatures = mutableListOf<TemperatureItem>()

            // Parse text meters & temperatures
            doc.select("section p, div p, li, td").forEach { elem ->
                val text = elem.ownText().ifEmpty { elem.text() }.trim()
                if (text.startsWith("Global CPU:")) {
                    globalCpu = text.substringAfter("Global CPU:").trimEnd('%', ' ').toFloatOrNull() ?: globalCpu
                } else if (text.startsWith("CPU") && text.contains(":") && !text.contains("Temperature", ignoreCase = true)) {
                    val coreVal = text.substringAfter(":").trimEnd('%', ' ').toFloatOrNull()
                    if (coreVal != null) {
                        cores.add(coreVal)
                    }
                } else if (text.contains("Temperature:", ignoreCase = true) ||
                    (text.contains("Temp:", ignoreCase = true) && (text.contains("°") || text.contains("C", ignoreCase = true))) ||
                    (text.contains("thermal", ignoreCase = true) && (text.contains("°") || text.contains("C", ignoreCase = true)))
                ) {
                    val label = if (text.contains(":")) text.substringBefore(":").trim() else "Thermal Zone ${temperatures.size + 1}"
                    val valPart = if (text.contains(":")) text.substringAfter(":") else text
                    val num = valPart.replace(Regex("""[^0-9.]"""), "").toFloatOrNull()
                    if (num != null && num in -20f..150f) {
                        if (temperatures.none { it.label.equals(label, ignoreCase = true) }) {
                            temperatures.add(TemperatureItem(label = label, tempCelsius = num))
                        }
                    }
                }
            }

            // If CPU temp was parsed from list or needs fallback
            val cpuFromTemps = temperatures.firstOrNull { it.label.contains("CPU", ignoreCase = true) }?.tempCelsius
            if (cpuFromTemps != null) {
                cpuTemp = cpuFromTemps
            } else if (temperatures.isNotEmpty()) {
                cpuTemp = temperatures.first().tempCelsius
            } else if (cpuTemp != null) {
                temperatures.add(TemperatureItem(label = "CPU Temperature", tempCelsius = cpuTemp))
            } else if (dataTemp.isNotEmpty()) {
                val t = dataTemp.first()
                cpuTemp = t
                temperatures.add(TemperatureItem(label = "CPU Temperature", tempCelsius = t))
            }

            // Parse RAM & Swap
            var ramUsed = 0L
            var ramTotal = 1L
            var swapUsed = 0L
            var swapTotal = 1L

            doc.select("section p").forEach { p ->
                val text = p.text()
                if (text.startsWith("RAM Usage:")) {
                    val parts = text.substringAfter("RAM Usage:").split("/")
                    if (parts.size == 2) {
                        ramUsed = parseByteString(parts[0].trim())
                        ramTotal = parseByteString(parts[1].trim())
                    }
                } else if (text.startsWith("Swap Usage:")) {
                    val parts = text.substringAfter("Swap Usage:").split("/")
                    if (parts.size == 2) {
                        swapUsed = parseByteString(parts[0].trim())
                        swapTotal = parseByteString(parts[1].trim())
                    }
                }
            }

            // Parse Disks
            val disks = mutableListOf<DiskInfoItem>()
            doc.select("section p").forEach { p ->
                val text = p.text()
                // Format: RootFS (/): 7.82 GB / 31.2 GB
                if (text.contains("(") && text.contains("):") && text.contains("/")) {
                    val name = text.substringBefore("(").trim()
                    val mount = text.substringAfter("(").substringBefore("):").trim()
                    val usages = text.substringAfter("):").split("/")
                    if (usages.size == 2) {
                        disks.add(
                            DiskInfoItem(
                                name = name.ifEmpty { "Disk" },
                                mountPoint = mount.ifEmpty { "/" },
                                used = parseByteString(usages[0].trim()),
                                total = parseByteString(usages[1].trim())
                            )
                        )
                    }
                }
            }

            val now = System.currentTimeMillis()
            val initialCpuPoints = if (dataCpu.isNotEmpty()) {
                dataCpu.mapIndexed { idx, v ->
                    MetricPoint(timestamp = now - (dataCpu.size - 1 - idx) * 2000L, value = v)
                }
            } else {
                listOf(MetricPoint(timestamp = now, value = globalCpu))
            }

            val initialRamPoints = if (dataRam.isNotEmpty()) {
                dataRam.mapIndexed { idx, v ->
                    MetricPoint(timestamp = now - (dataRam.size - 1 - idx) * 2000L, value = v)
                }
            } else {
                val ramPct = if (ramTotal > 0L) (ramUsed.toFloat() / ramTotal.toFloat()) * 100f else 0f
                listOf(MetricPoint(timestamp = now, value = ramPct))
            }

            val initialTempPoints = if (dataTemp.isNotEmpty()) {
                dataTemp.mapIndexed { idx, v ->
                    MetricPoint(timestamp = now - (dataTemp.size - 1 - idx) * 2000L, value = v)
                }
            } else if (cpuTemp != null) {
                listOf(MetricPoint(timestamp = now, value = cpuTemp))
            } else {
                emptyList()
            }

            val initialSentPoints = if (dataSent.isNotEmpty()) {
                dataSent.mapIndexed { idx, v ->
                    MetricPoint(timestamp = now - (dataSent.size - 1 - idx) * 2000L, value = v)
                }
            } else {
                emptyList()
            }

            val initialRecvPoints = if (dataRecv.isNotEmpty()) {
                dataRecv.mapIndexed { idx, v ->
                    MetricPoint(timestamp = now - (dataRecv.size - 1 - idx) * 2000L, value = v)
                }
            } else {
                emptyList()
            }

            Result.success(
                SystemStats(
                    cpuGlobal = globalCpu,
                    cpuCores = cores,
                    cpuTemp = cpuTemp,
                    temperatures = temperatures,
                    ramUsed = ramUsed,
                    ramTotal = if (ramTotal > 0L) ramTotal else 1L,
                    swapUsed = swapUsed,
                    swapTotal = if (swapTotal > 0L) swapTotal else 1L,
                    disks = disks,
                    netSentRate = dataSent.firstOrNull()?.toLong() ?: 0L,
                    netRecvRate = dataRecv.firstOrNull()?.toLong() ?: 0L,
                    historyCpu = initialCpuPoints,
                    historyRam = initialRamPoints,
                    historyTemp = initialTempPoints,
                    historyNetSent = initialSentPoints,
                    historyNetRecv = initialRecvPoints
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchProcesses(): Result<List<ProcessItem>> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/process")
                    .get()
                    .build()
            }
            if (!response.isSuccessful) {
                response.close()
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val html = response.body?.string() ?: ""
            response.close()
            val doc = Jsoup.parse(html)

            val processes = mutableListOf<ProcessItem>()
            doc.select("table tr").drop(1).forEach { tr ->
                val tds = tr.select("td")
                if (tds.size >= 5) {
                    val pid = tds[0].text().trim().toIntOrNull() ?: 0
                    val name = tds[1].text().trim()
                    val statusStr = tds[2].text().trim().lowercase()
                    val cpu = tds[3].text().trimEnd('%').toFloatOrNull() ?: 0f
                    val ramBytes = parseByteString(tds[4].text().trim())

                    val status = when {
                        statusStr.contains("run") -> ProcessStatus.Running
                        statusStr.contains("sleep") -> ProcessStatus.Sleeping
                        statusStr.contains("stop") || statusStr.contains("pause") -> ProcessStatus.Paused
                        else -> ProcessStatus.Other
                    }

                    if (pid > 0 && name.isNotEmpty()) {
                        processes.add(ProcessItem(pid, name, cpu, ramBytes, status))
                    }
                }
            }

            Result.success(processes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendProcessSignal(pid: Int, signal: ProcessSignal): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val sigName = signal.wireName
            val url = "${server.baseUrl}/process/signal?pid=$pid&signal=$sigName"
            val response = executeAuthenticated {
                Request.Builder()
                    .url(url)
                    .post("".toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
                    .build()
            }
            val ok = response.isSuccessful
            response.close()
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchServices(): Result<List<ServiceItem>> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/service")
                    .get()
                    .build()
            }
            if (!response.isSuccessful) {
                response.close()
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val html = response.body?.string() ?: ""
            response.close()
            val doc = Jsoup.parse(html)

            val services = mutableListOf<ServiceItem>()
            doc.select("table tr").drop(1).forEach { tr ->
                val tds = tr.select("td")
                if (tds.size >= 4) {
                    val name = tds[0].text().trim()
                    val statusText = tds[1].text().trim().lowercase()
                    val errLog = tds[2].select("pre").text().trim()
                    val startTime = tds[3].text().trim()

                    val status = when {
                        statusText.contains("active") -> ServiceStatus.Active
                        statusText.contains("inactive") || statusText.contains("dead") -> ServiceStatus.Inactive
                        statusText.contains("failed") -> ServiceStatus.Failed
                        else -> ServiceStatus.Unknown
                    }

                    if (name.isNotEmpty()) {
                        services.add(ServiceItem(name, status, startTime, errLog))
                    }
                }
            }

            Result.success(services)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSoftware(): Result<List<SoftwareItem>> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/software")
                    .get()
                    .build()
            }
            if (!response.isSuccessful) {
                response.close()
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val html = response.body?.string() ?: ""
            response.close()
            val doc = Jsoup.parse(html)

            val list = mutableListOf<SoftwareItem>()
            val tables = doc.select("table")
            tables.forEachIndexed { index, table ->
                // Table 0: Uninstalled (Install Software), Table 1: Installed (Uninstall Software)
                val isInstalled = index == 1
                table.select("tr").drop(1).forEach { tr ->
                    val tds = tr.select("td")
                    if (tds.size >= 4) {
                        val name = tds[0].text().trim()
                        val desc = tds[1].text().trim()
                        val deps = tds[2].text().trim()
                        val docs = tds[3].select("a").attr("href").ifEmpty { tds[3].text().trim() }
                        val lastTd = tds.last()
                        val nmBind = lastTd?.select("input[type=checkbox]")?.attr("nm-bind") ?: ""
                        val idFromBind = Regex("""software\.set\(["']?(\d+)["']?""").find(nmBind)?.groupValues?.get(1)?.toIntOrNull()
                        val id = idFromBind ?: (list.size + 1)

                        if (name.isNotEmpty()) {
                            list.add(
                                SoftwareItem(
                                    id = id,
                                    name = name,
                                    desc = desc,
                                    deps = deps,
                                    docs = docs,
                                    isInstalled = isInstalled
                                )
                            )
                        }
                    }
                }
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleSoftware(id: Int, isInstalled: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val action = if (isInstalled) "uninstall" else "install"
            val form = FormBody.Builder()
                .add("software", id.toString())
                .add("action", action)
                .build()
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/software")
                    .post(form)
                    .build()
            }
            val ok = response.isSuccessful
            response.close()
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchHostInfo(): Result<HostInfo> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/management")
                    .get()
                    .build()
            }
            if (!response.isSuccessful) {
                response.close()
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val html = response.body?.string() ?: ""
            response.close()
            val doc = Jsoup.parse(html)

            var hostname = "DietPi"
            var nic = "eth0"
            var arch = "aarch64"
            var uptimeSec = 0L
            var kernel = "Linux"
            var osVersion = "Debian"
            var dietPiVersion = "v9.x"
            var numPkgs = 0

            doc.select(".management-table tr").forEach { tr ->
                val tds = tr.select("td")
                if (tds.size == 2) {
                    val key = tds[0].text().trim().lowercase()
                    val value = tds[1].text().trim()
                    when {
                        key.contains("hostname") -> hostname = value
                        key.contains("network interface") -> nic = value
                        key.contains("uptime") -> uptimeSec = parseUptimeSeconds(value)
                        key.contains("installed packages") -> numPkgs = value.toIntOrNull() ?: numPkgs
                        key.contains("os version") -> osVersion = value
                        key.contains("kernel version") -> kernel = value
                        key.contains("dietpi version") -> dietPiVersion = value
                        key.contains("architecture") -> arch = value
                    }
                }
            }

            Result.success(
                HostInfo(
                    hostname = hostname,
                    nic = nic,
                    arch = arch,
                    uptimeSeconds = uptimeSec,
                    kernel = kernel,
                    osVersion = osVersion,
                    dietPiVersion = dietPiVersion,
                    packageCount = numPkgs
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchFiles(path: String): Result<List<FileBrowserItem>> = withContext(Dispatchers.IO) {
        try {
            val targetPath = if (path.isBlank()) "/root" else path
            val url = "${server.baseUrl}/browser?path=${java.net.URLEncoder.encode(targetPath, "UTF-8")}"
            val response = executeAuthenticated {
                Request.Builder()
                    .url(url)
                    .header("nm-request", "true")
                    .get()
                    .build()
            }
            if (!response.isSuccessful) {
                response.close()
                return@withContext Result.failure(Exception("HTTP ${response.code} fetching directory"))
            }
            val html = response.body?.string() ?: ""
            response.close()
            val doc = Jsoup.parse(html)
            val items = mutableListOf<FileBrowserItem>()

            // DietPi-Dashboard generates rows in #browser-inner table tr
            doc.select("#browser-inner tr").forEach { tr ->
                // Skip header row if it contains th
                if (tr.select("th").isNotEmpty()) return@forEach

                val itemPathAttr = tr.attr("data-path")
                val kindAttr = tr.attr("data-kind")
                val isHiddenAttr = tr.hasAttr("data-hidden")

                val tds = tr.select("td")
                if (tds.isNotEmpty()) {
                    val nameFromTd = tds[0].text().trim()
                    val sizeStr = if (tds.size > 1) tds[1].text().trim() else "--"

                    val isDirFromIcon = tr.html().contains("fa6-solid-folder") ||
                            tr.select("a").attr("href").contains("browser?path=") ||
                            kindAttr.equals("Directory", ignoreCase = true)

                    val itemPath = if (itemPathAttr.isNotBlank()) {
                        itemPathAttr
                    } else {
                        val name = nameFromTd
                        if (targetPath.endsWith("/")) "$targetPath$name" else "$targetPath/$name"
                    }

                    val itemName = if (nameFromTd.isNotBlank()) {
                        nameFromTd
                    } else {
                        itemPath.substringAfterLast('/')
                    }

                    if (itemName.isNotEmpty() && itemName != "." && itemName != "..") {
                        val kind = when {
                            isDirFromIcon -> FileKind.Directory
                            kindAttr.equals("TextFile", ignoreCase = true) -> FileKind.TextFile
                            kindAttr.equals("BinaryFile", ignoreCase = true) -> FileKind.BinaryFile
                            kindAttr.equals("Special", ignoreCase = true) -> FileKind.Special
                            isTextExtension(itemName) -> FileKind.TextFile
                            else -> FileKind.BinaryFile
                        }

                        val parsedSize = if (kind == FileKind.Directory) null else parseByteString(sizeStr)

                        items.add(
                            FileBrowserItem(
                                path = itemPath,
                                name = itemName,
                                isDirectory = (kind == FileKind.Directory),
                                size = parsedSize,
                                kind = kind,
                                isHidden = isHiddenAttr || itemName.startsWith(".")
                            )
                        )
                    }
                }
            }

            // Sort directories first, then alphabetically
            val sorted = items.sortedWith(
                compareBy<FileBrowserItem> { !it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )

            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTextFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // First try direct download endpoint for exact raw UTF-8 content
            val downloadUrl = "${server.baseUrl}/browser/actions/download?path=${java.net.URLEncoder.encode(path, "UTF-8")}"
            val downloadResp = executeAuthenticated {
                Request.Builder()
                    .url(downloadUrl)
                    .get()
                    .build()
            }
            if (downloadResp.isSuccessful) {
                val bytes = downloadResp.body?.bytes()
                downloadResp.close()
                if (bytes != null) {
                    return@withContext Result.success(String(bytes, Charsets.UTF_8))
                }
            } else {
                downloadResp.close()
            }

            // Fallback to /browser/file
            val fileUrl = "${server.baseUrl}/browser/file?path=${java.net.URLEncoder.encode(path, "UTF-8")}"
            val response = executeAuthenticated {
                Request.Builder()
                    .url(fileUrl)
                    .get()
                    .build()
            }
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@withContext Result.failure(Exception("Failed to open file (HTTP $code)"))
            }
            val html = response.body?.string() ?: ""
            response.close()

            val doc = Jsoup.parse(html)
            val textarea = doc.selectFirst("code-editor textarea, textarea")
            val content = textarea?.wholeText() ?: textarea?.text() ?: html
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveTextFile(path: String, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("path", path)
                .add("data", content)
                .build()
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/browser/file/save")
                    .post(form)
                    .build()
            }
            val success = response.isSuccessful || response.code in 300..399
            response.close()
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to save file (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createNewFile(parentPath: String, name: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("path", parentPath)
                .add("name", name)
                .build()
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/browser/actions/new-file")
                    .post(form)
                    .build()
            }
            val success = response.isSuccessful || response.code in 300..399
            response.close()
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to create file (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createNewFolder(parentPath: String, name: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("path", parentPath)
                .add("name", name)
                .build()
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/browser/actions/new-folder")
                    .post(form)
                    .build()
            }
            val success = response.isSuccessful || response.code in 300..399
            response.close()
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to create folder (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFileOrFolder(path: String, newName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("path", path)
                .add("new_name", newName)
                .build()
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/browser/actions/rename")
                    .post(form)
                    .build()
            }
            val success = response.isSuccessful || response.code in 300..399
            response.close()
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to rename (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("path", path)
                .build()
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/browser/actions/delete-file")
                    .post(form)
                    .build()
            }
            val success = response.isSuccessful || response.code in 300..399
            response.close()
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete file (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("path", path)
                .build()
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/browser/actions/delete-folder")
                    .post(form)
                    .build()
            }
            val success = response.isSuccessful || response.code in 300..399
            response.close()
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete folder (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFileBytes(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = "${server.baseUrl}/browser/actions/download?path=${java.net.URLEncoder.encode(path, "UTF-8")}"
            val response = executeAuthenticated {
                Request.Builder()
                    .url(url)
                    .get()
                    .build()
            }
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@withContext Result.failure(Exception("Download failed (HTTP $code)"))
            }
            val bytes = response.body?.bytes()
            response.close()
            if (bytes != null) {
                Result.success(bytes)
            } else {
                Result.failure(Exception("Empty file response"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFile(parentPath: String, name: String, bytes: ByteArray): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val form = FormBody.Builder()
                .add("path", parentPath)
                .add("name", name)
                .add("data", base64Data)
                .build()
            val response = executeAuthenticated {
                Request.Builder()
                    .url("${server.baseUrl}/browser/actions/upload")
                    .post(form)
                    .build()
            }
            val success = response.isSuccessful || response.code in 300..399
            response.close()
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("Upload failed (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isTextExtension(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".conf") ||
                lower.endsWith(".json") || lower.endsWith(".sh") || lower.endsWith(".bash") ||
                lower.endsWith(".py") || lower.endsWith(".yaml") || lower.endsWith(".yml") ||
                lower.endsWith(".toml") || lower.endsWith(".ini") || lower.endsWith(".service") ||
                lower.endsWith(".md") || lower.endsWith(".cfg") || lower.endsWith(".env") ||
                lower.endsWith(".xml") || lower.endsWith(".html") || lower.endsWith(".css") ||
                lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".csv") ||
                lower.equals("dietpi.txt") || lower.equals("config.txt") || lower.equals(".bashrc") ||
                lower.equals(".profile") || lower.equals("hosts") || lower.equals("fstab")
    }

    private fun parseFloats(str: String?): List<Float> {
        if (str.isNullOrBlank()) return emptyList()
        return str.trim('[', ']', ' ')
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
    }

    private fun parseByteString(str: String): Long {
        if (str.isBlank()) return 0L
        val clean = str.replace(",", "").trim()
        val parts = clean.split(" ")
        if (parts.size < 2) {
            return clean.filter { it.isDigit() }.toLongOrNull() ?: 0L
        }
        val number = parts[0].toDoubleOrNull() ?: return 0L
        val unit = parts[1].uppercase()
        return when {
            unit.startsWith("TB") || unit.startsWith("TIB") -> (number * 1024L * 1024L * 1024L * 1024L).toLong()
            unit.startsWith("GB") || unit.startsWith("GIB") -> (number * 1024L * 1024L * 1024L).toLong()
            unit.startsWith("MB") || unit.startsWith("MIB") -> (number * 1024L * 1024L).toLong()
            unit.startsWith("KB") || unit.startsWith("KIB") -> (number * 1024L).toLong()
            else -> number.toLong()
        }
    }

    private fun parseUptimeSeconds(str: String): Long {
        var totalSec = 0L
        val regex = Regex("""(\d+)\s*(day|d|hour|h|minute|min|m|sec|s)""")
        regex.findAll(str.lowercase()).forEach { match ->
            val num = match.groupValues[1].toLongOrNull() ?: 0L
            val unit = match.groupValues[2]
            when {
                unit.startsWith("d") -> totalSec += num * 86400
                unit.startsWith("h") -> totalSec += num * 3600
                unit.startsWith("m") -> totalSec += num * 60
                unit.startsWith("s") -> totalSec += num
            }
        }
        return if (totalSec > 0) totalSec else 3600L
    }

    fun openTerminalStream(): kotlinx.coroutines.flow.Flow<ByteArray> = kotlinx.coroutines.flow.flow {
        ensureAuthenticated()
        val streamingClient = okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val req = Request.Builder()
            .url("${server.baseUrl}/terminal/stream")
            .addHeader("Accept", "application/octet-stream")
            .build()

        var response = streamingClient.newCall(req).execute()
        val finalPath = response.request.url.encodedPath
        if (finalPath.contains("login") || response.code in listOf(401, 403)) {
            response.close()
            login()
            response = streamingClient.newCall(req).execute()
        }
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw java.io.IOException("Terminal stream failed with HTTP $code")
        }

        val source = response.body?.source() ?: throw java.io.IOException("Empty terminal stream response body")
        // Signal that the HTTP stream connection is established
        emit(ByteArray(0))
        val buffer = ByteArray(4096)
        try {
            while (!source.exhausted()) {
                val read = source.read(buffer)
                if (read > 0) {
                    emit(buffer.copyOf(read))
                } else if (read < 0) {
                    break
                }
            }
        } finally {
            try { response.close() } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    suspend fun writeTerminal(input: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = input.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())
            val req = Request.Builder()
                .url("${server.baseUrl}/terminal/write")
                .post(body)
                .build()
            val resp = executeAuthenticated { req }
            resp.use {
                if (!it.isSuccessful && it.code != 200) {
                    throw java.io.IOException("Terminal write failed: ${it.code}")
                }
            }
        }
    }

    private val terminalFastClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    suspend fun resizeTerminal(cols: Int, rows: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (cols <= 0 || rows <= 0) return@runCatching
            val form = FormBody.Builder()
                .add("cols", cols.toString())
                .add("rows", rows.toString())
                .build()
            val req = Request.Builder()
                .url("${server.baseUrl}/terminal/resize")
                .post(form)
                .build()
            // Use terminalFastClient directly so any resize failure or redirect does not clear cookies or trigger re-auth
            terminalFastClient.newCall(req).execute().use { _ -> }
        }
    }
}
