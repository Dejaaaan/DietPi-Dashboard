package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ServerEntity
import com.example.data.model.*
import com.example.data.repository.DietPiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TerminalAction {
    REDRAW,
    RESET,
    RELOAD,
    CLEAR
}

class DietPiViewModel(private val repository: DietPiRepository) : ViewModel() {

    private val _terminalAction = MutableSharedFlow<TerminalAction>(extraBufferCapacity = 16)
    val terminalAction: SharedFlow<TerminalAction> = _terminalAction.asSharedFlow()

    val allServers: StateFlow<List<ServerEntity>> = repository.allServers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeServer = MutableStateFlow<ServerEntity?>(null)
    val activeServer: StateFlow<ServerEntity?> = _activeServer.asStateFlow()

    private val _isServerInitComplete = MutableStateFlow(false)
    val isServerInitComplete: StateFlow<Boolean> = _isServerInitComplete.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _systemStats = MutableStateFlow(SystemStats())
    val systemStats: StateFlow<SystemStats> = _systemStats.asStateFlow()

    private val _hostInfo = MutableStateFlow(HostInfo())
    val hostInfo: StateFlow<HostInfo> = _hostInfo.asStateFlow()

    private val _processes = MutableStateFlow<List<ProcessItem>>(emptyList())
    val processes: StateFlow<List<ProcessItem>> = _processes.asStateFlow()

    private val _services = MutableStateFlow<List<ServiceItem>>(emptyList())
    val services: StateFlow<List<ServiceItem>> = _services.asStateFlow()

    private val _software = MutableStateFlow<List<SoftwareItem>>(emptyList())
    val software: StateFlow<List<SoftwareItem>> = _software.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingInitial = MutableStateFlow(false)
    val isLoadingInitial: StateFlow<Boolean> = _isLoadingInitial.asStateFlow()

    private val _isPollingActive = MutableStateFlow(true)
    val isPollingActive: StateFlow<Boolean> = _isPollingActive.asStateFlow()

    private val _powerOperationState = MutableStateFlow<PowerOperationState>(PowerOperationState.Idle)
    val powerOperationState: StateFlow<PowerOperationState> = _powerOperationState.asStateFlow()

    // File Browser states
    private val _currentDirectoryPath = MutableStateFlow("/root")
    val currentDirectoryPath: StateFlow<String> = _currentDirectoryPath.asStateFlow()

    private val _directoryHistory = MutableStateFlow<List<String>>(emptyList())
    val directoryHistory: StateFlow<List<String>> = _directoryHistory.asStateFlow()

    private val _directoryItems = MutableStateFlow<List<FileBrowserItem>>(emptyList())
    val directoryItems: StateFlow<List<FileBrowserItem>> = _directoryItems.asStateFlow()

    private val _webUiZoomLevel = MutableStateFlow(repository.getWebUiZoomLevel())
    val webUiZoomLevel: StateFlow<Int> = _webUiZoomLevel.asStateFlow()

    private val _isFileBrowserLoading = MutableStateFlow(false)
    val isFileBrowserLoading: StateFlow<Boolean> = _isFileBrowserLoading.asStateFlow()

    private val _fileBrowserError = MutableStateFlow<String?>(null)
    val fileBrowserError: StateFlow<String?> = _fileBrowserError.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    // Text File Editor states
    private val _editingFilePath = MutableStateFlow<String?>(null)
    val editingFilePath: StateFlow<String?> = _editingFilePath.asStateFlow()

    private val _editingFileContent = MutableStateFlow<String?>(null)
    val editingFileContent: StateFlow<String?> = _editingFileContent.asStateFlow()

    private val _isEditingFileLoading = MutableStateFlow(false)
    val isEditingFileLoading: StateFlow<Boolean> = _isEditingFileLoading.asStateFlow()

    private val _isSavingFile = MutableStateFlow(false)
    val isSavingFile: StateFlow<Boolean> = _isSavingFile.asStateFlow()

    private val _editingFileError = MutableStateFlow<String?>(null)
    val editingFileError: StateFlow<String?> = _editingFileError.asStateFlow()

    fun getActiveServerCookies(): List<okhttp3.Cookie> {
        return repository.getActiveServerCookies()
    }

    val isScanningNetwork: StateFlow<Boolean> = repository.discoveryManager.isScanning
    val discoveredNodes: StateFlow<List<com.example.data.discovery.DiscoveredDietPi>> = repository.discoveryManager.discoveredNodes
    val scanStatus: StateFlow<String> = repository.discoveryManager.scanStatus
    val candidateSubnets: List<String> get() = repository.discoveryManager.getCandidateSubnets()

    private var pollingJob: Job? = null
    private val terminalInputChannel = Channel<String>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            try {
                val defaultServer = repository.getDefaultServer()
                if (defaultServer != null) {
                    selectServer(defaultServer)
                } else {
                    val servers = repository.allServers.first()
                    if (servers.isNotEmpty()) {
                        selectServer(servers.first())
                    }
                }
                // Preload software catalog so it is immediately browsable
                val softRes = repository.fetchSoftware()
                softRes.onSuccess { _software.value = it }
            } catch (e: Exception) {
                android.util.Log.e("DietPiVM", "Error during server initialization", e)
            } finally {
                _isServerInitComplete.value = true
            }
        }

        // Process terminal keystrokes strictly in FIFO order, batching any queued characters into single HTTP requests
        viewModelScope.launch(Dispatchers.IO) {
            for (first in terminalInputChannel) {
                val sb = StringBuilder(first)
                while (true) {
                    val next = terminalInputChannel.tryReceive().getOrNull() ?: break
                    sb.append(next)
                }
                val payload = sb.toString()
                if (payload.isNotEmpty()) {
                    try {
                        repository.writeTerminal(payload)
                    } catch (e: Exception) {
                        Log.w("DietPiViewModel", "Terminal write failed: ${e.message}")
                    }
                }
            }
        }
    }

    fun selectServer(server: ServerEntity) {
        if (_activeServer.value?.id != server.id) {
            val oldTerminalJob = terminalJob
            terminalJob = null
            oldTerminalJob?.cancel()
            _terminalStatus.value = TerminalStatus.Disconnected
            clearTerminalBuffer()
            _currentDirectoryPath.value = "/root"
            _directoryItems.value = emptyList()
            _directoryHistory.value = emptyList()
            _editingFilePath.value = null
            _editingFileContent.value = null
        }
        _activeServer.value = server
        repository.setActiveServer(server)
        startPolling()
    }

    fun addServer(server: ServerEntity) {
        viewModelScope.launch {
            val id = repository.insertServer(server)
            selectServer(server.copy(id = id))
        }
    }

    fun updateServer(server: ServerEntity) {
        viewModelScope.launch {
            repository.updateServer(server)
            if (_activeServer.value?.id == server.id) {
                selectServer(server)
            }
        }
    }

    suspend fun testServer(server: ServerEntity): Result<Long> {
        return repository.testServerConnection(server)
    }

    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch {
            repository.deleteServer(server)
            if (_activeServer.value?.id == server.id) {
                val remaining = allServers.value.filter { it.id != server.id }
                if (remaining.isNotEmpty()) {
                    selectServer(remaining.first())
                } else {
                    _activeServer.value = null
                    repository.setActiveServer(null)
                    _connectionState.value = ConnectionState.Idle
                }
            }
        }
    }

    fun startNetworkDiscovery(customSubnet: String? = null) {
        repository.discoveryManager.startDiscovery(viewModelScope, customSubnet)
    }

    fun stopNetworkDiscovery() {
        repository.discoveryManager.stopDiscovery()
    }

    fun togglePolling() {
        val next = !_isPollingActive.value
        _isPollingActive.value = next
        if (next) {
            startPolling()
        } else {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchDataOnce()
            _isRefreshing.value = false
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            _connectionState.value = ConnectionState.Connecting
            _isLoadingInitial.value = true

            // Test connection first
            val current = _activeServer.value
            if (current != null) {
                val testRes = repository.testServerConnection(current)
                testRes.fold(
                    onSuccess = { latency ->
                        _connectionState.value = ConnectionState.Connected(latency)
                    },
                    onFailure = { err ->
                        _connectionState.value = ConnectionState.Error(err.localizedMessage ?: "Connection timed out")
                    }
                )
            } else {
                _connectionState.value = ConnectionState.Idle
            }

            // Initial full load
            try {
                fetchDataOnce()
            } finally {
                _isLoadingInitial.value = false
            }

            // Continuous telemetry polling
            while (isActive && _isPollingActive.value) {
                delay(2000L)
                fetchTelemetryOnly()
            }
        }
    }

    private suspend fun fetchDataOnce() {
        val statsRes = repository.fetchSystemStats()
        statsRes.onSuccess { updateStats(it) }

        val hostRes = repository.fetchHostInfo()
        hostRes.onSuccess { _hostInfo.value = it }

        val procRes = repository.fetchProcesses()
        procRes.onSuccess { _processes.value = it }

        val servRes = repository.fetchServices()
        servRes.onSuccess { _services.value = it }

        val softRes = repository.fetchSoftware()
        softRes.onSuccess { _software.value = it }
    }

    private suspend fun fetchTelemetryOnly() {
        val statsRes = repository.fetchSystemStats()
        statsRes.onSuccess { updateStats(it) }

        val procRes = repository.fetchProcesses()
        procRes.onSuccess { _processes.value = it }
    }

    private fun updateStats(incoming: SystemStats) {
        _systemStats.value = mergeHistoricalStats(_systemStats.value, incoming)
    }

    private fun mergeHistoricalStats(current: SystemStats?, incoming: SystemStats): SystemStats {
        val now = System.currentTimeMillis()
        val maxPoints = 240 // ~8 minutes at 2-second interval

        // CPU History: append live reading
        val cpuHistory = (current?.historyCpu ?: emptyList()).toMutableList()
        if (cpuHistory.isEmpty() && incoming.historyCpu.isNotEmpty()) {
            cpuHistory.addAll(incoming.historyCpu)
        }
        cpuHistory.add(MetricPoint(timestamp = now, value = incoming.cpuGlobal))
        val trimmedCpu = if (cpuHistory.size > maxPoints) cpuHistory.takeLast(maxPoints) else cpuHistory

        // RAM History: append live reading
        val ramHistory = (current?.historyRam ?: emptyList()).toMutableList()
        if (ramHistory.isEmpty() && incoming.historyRam.isNotEmpty()) {
            ramHistory.addAll(incoming.historyRam)
        }
        ramHistory.add(MetricPoint(timestamp = now, value = incoming.ramPercent))
        val trimmedRam = if (ramHistory.size > maxPoints) ramHistory.takeLast(maxPoints) else ramHistory

        // Temperature History: append live reading if available
        val tempHistory = (current?.historyTemp ?: emptyList()).toMutableList()
        if (incoming.cpuTemp != null) {
            if (tempHistory.isEmpty() && incoming.historyTemp.isNotEmpty()) {
                tempHistory.addAll(incoming.historyTemp)
            }
            tempHistory.add(MetricPoint(timestamp = now, value = incoming.cpuTemp))
        }
        val trimmedTemp = if (tempHistory.size > maxPoints) tempHistory.takeLast(maxPoints) else tempHistory

        // Network Recv History: append live rate (bytes/sec)
        val netRecvHistory = (current?.historyNetRecv ?: emptyList()).toMutableList()
        if (netRecvHistory.isEmpty() && incoming.historyNetRecv.isNotEmpty()) {
            netRecvHistory.addAll(incoming.historyNetRecv)
        }
        netRecvHistory.add(MetricPoint(timestamp = now, value = incoming.netRecvRate.toFloat()))
        val trimmedNetRecv = if (netRecvHistory.size > maxPoints) netRecvHistory.takeLast(maxPoints) else netRecvHistory

        // Network Sent History: append live rate (bytes/sec)
        val netSentHistory = (current?.historyNetSent ?: emptyList()).toMutableList()
        if (netSentHistory.isEmpty() && incoming.historyNetSent.isNotEmpty()) {
            netSentHistory.addAll(incoming.historyNetSent)
        }
        netSentHistory.add(MetricPoint(timestamp = now, value = incoming.netSentRate.toFloat()))
        val trimmedNetSent = if (netSentHistory.size > maxPoints) netSentHistory.takeLast(maxPoints) else netSentHistory

        // Preserve and merge temperatures
        val mergedTemps = if (incoming.temperatures.isNotEmpty()) {
            incoming.temperatures
        } else if (current?.temperatures?.isNotEmpty() == true) {
            current.temperatures
        } else if (incoming.cpuTemp != null) {
            listOf(TemperatureItem("CPU Temperature", incoming.cpuTemp))
        } else {
            emptyList()
        }

        return incoming.copy(
            historyCpu = trimmedCpu,
            historyRam = trimmedRam,
            historyTemp = trimmedTemp,
            historyNetRecv = trimmedNetRecv,
            historyNetSent = trimmedNetSent,
            temperatures = mergedTemps
        )
    }

    fun sendProcessSignal(pid: Int, signal: ProcessSignal) {
        viewModelScope.launch {
            repository.sendProcessSignal(pid, signal)
            val updatedProcs = repository.fetchProcesses()
            updatedProcs.onSuccess { _processes.value = it }
        }
    }

    fun toggleSoftware(id: Int) {
        viewModelScope.launch {
            val isInstalled = _software.value.find { it.id == id }?.isInstalled ?: false
            repository.toggleSoftware(id, isInstalled)
            val updated = repository.fetchSoftware()
            updated.onSuccess { _software.value = it }
        }
    }

    // Terminal Session Management
    private val _terminalStatus = MutableStateFlow<TerminalStatus>(TerminalStatus.Disconnected)
    val terminalStatus: StateFlow<TerminalStatus> = _terminalStatus.asStateFlow()

    // 16,384-byte rolling FIFO ring buffer mirroring DietPi-Dashboard term_buf
    private val terminalRingBufferSize = 16384
    private val terminalRingBuffer = ArrayDeque<Byte>(terminalRingBufferSize)
    private val ringBufferLock = Any()

    private val _terminalOutput = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 256)
    val terminalOutput: SharedFlow<ByteArray> = _terminalOutput.asSharedFlow()

    fun openTerminalOutputFlow(): Flow<ByteArray> = flow {
        val snapshot = synchronized(ringBufferLock) {
            terminalRingBuffer.toByteArray()
        }
        if (snapshot.isNotEmpty()) {
            emit(snapshot)
        }
        emitAll(_terminalOutput)
    }

    fun clearTerminalBuffer() {
        synchronized(ringBufferLock) {
            terminalRingBuffer.clear()
        }
    }

    private suspend fun appendTerminalOutput(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        synchronized(ringBufferLock) {
            for (b in bytes) {
                if (terminalRingBuffer.size >= terminalRingBufferSize) {
                    terminalRingBuffer.removeFirst()
                }
                terminalRingBuffer.addLast(b)
            }
        }
        // Active data incoming from terminal stream guarantees Connected status
        if (_terminalStatus.value !is TerminalStatus.Connected) {
            _terminalStatus.value = TerminalStatus.Connected("DietPi Live PTY")
        }
        _terminalOutput.emit(bytes)
    }

    private var terminalJob: Job? = null
    @Volatile
    private var currentTerminalSessionId: Long = 0L

    fun startTerminalSession() {
        if (terminalJob?.isActive == true && _terminalStatus.value is TerminalStatus.Connected) {
            return
        }
        val server = _activeServer.value
        if (server == null) {
            _terminalStatus.value = TerminalStatus.Disconnected
            return
        }

        val oldJob = terminalJob
        terminalJob = null
        oldJob?.cancel()

        val sessionId = System.currentTimeMillis()
        currentTerminalSessionId = sessionId

        terminalJob = viewModelScope.launch {
            _terminalStatus.value = TerminalStatus.Connecting
            var retryCount = 0
            val maxSilentRetries = 3

            while (isActive && currentTerminalSessionId == sessionId) {
                val streamFlow = repository.openTerminalStream()

                if (streamFlow != null) {
                    try {
                        retryCount = 0
                        streamFlow.collect { chunk ->
                            if (currentTerminalSessionId != sessionId || !isActive) return@collect
                            if (_terminalStatus.value !is TerminalStatus.Connected) {
                                _terminalStatus.value = TerminalStatus.Connected("DietPi Live PTY")
                            }
                            if (chunk.isNotEmpty()) {
                                appendTerminalOutput(chunk)
                            }
                        }

                        if (currentTerminalSessionId != sessionId || !isActive) return@launch
                        Log.d("DietPiViewModel", "Terminal stream ended cleanly from server side")
                        // If stream completed normally without error, check if we should reconnect or mark disconnected
                        if (retryCount < maxSilentRetries) {
                            retryCount++
                            delay(500L * retryCount)
                            continue
                        } else {
                            if (currentTerminalSessionId == sessionId && isActive) {
                                _terminalStatus.value = TerminalStatus.Disconnected
                            }
                            break
                        }
                    } catch (e: CancellationException) {
                        // Normal coroutine cancellation (tab switch, session restart, or server switch).
                        // Do NOT set _terminalStatus to Disconnected because a new session may be starting.
                        throw e
                    } catch (e: Exception) {
                        if (currentTerminalSessionId != sessionId || !isActive) return@launch
                        val errMsg = e.localizedMessage ?: e.javaClass.simpleName ?: "Connection error"
                        Log.w("DietPiViewModel", "Terminal stream error (retry $retryCount): $errMsg", e)
                        
                        if (retryCount < maxSilentRetries) {
                            retryCount++
                            // Brief reconnect attempt before surfacing error banner to user
                            delay(1000L * retryCount)
                            continue
                        } else {
                            if (currentTerminalSessionId == sessionId && isActive) {
                                _terminalStatus.value = TerminalStatus.Error(errMsg)
                                val notice = "\r\n\u001B[1;31m[!] Terminal disconnected: $errMsg\u001B[0m\r\n"
                                appendTerminalOutput(notice.toByteArray(Charsets.UTF_8))
                            }
                            break
                        }
                    }
                } else {
                    if (currentTerminalSessionId == sessionId && isActive) {
                        _terminalStatus.value = TerminalStatus.Disconnected
                        val notice = "\r\n\u001B[1;33m[!] No active DietPi server connected. Select or add a server to start terminal.\u001B[0m\r\n"
                        appendTerminalOutput(notice.toByteArray(Charsets.UTF_8))
                    }
                    break
                }
            }
        }
    }

    fun sendTerminalInput(data: String) {
        if (_terminalStatus.value !is TerminalStatus.Connected && _activeServer.value != null) {
            startTerminalSession()
        }
        terminalInputChannel.trySend(data)
    }

    private var lastResizedCols: Int = -1
    private var lastResizedRows: Int = -1
    private var lastResizeTimestamp: Long = 0L

    fun resizeTerminal(cols: Int, rows: Int, force: Boolean = false) {
        if (cols <= 0 || rows <= 0) return
        val safeCols = maxOf(80, cols)
        val safeRows = maxOf(24, rows)
        val now = System.currentTimeMillis()
        // Send if dimensions changed, if forced, or if it's been more than 15s to keep PTY synced
        if (!force && safeCols == lastResizedCols && safeRows == lastResizedRows && (now - lastResizeTimestamp < 15000L)) {
            return
        }
        lastResizedCols = safeCols
        lastResizedRows = safeRows
        lastResizeTimestamp = now
        viewModelScope.launch {
            repository.resizeTerminal(safeCols, safeRows)
        }
    }

    fun restartTerminalSession() {
        val oldJob = terminalJob
        terminalJob = null
        oldJob?.cancel()
        _terminalStatus.value = TerminalStatus.Connecting
        startTerminalSession()
    }

    /**
     * Refreshes the Terminal screen consistently with other screens:
     * - Triggers spinning refresh animation and top progress indicator
     * - Re-verifies server connectivity and updates background telemetry
     * - If terminal was disconnected/error, reconnects cleanly
     * - If already connected, redraws xterm buffer and resyncs screen dimensions without killing active shell
     */
    fun refreshTerminalTab() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // 1. Refresh active server connectivity and general telemetry
                val current = _activeServer.value
                if (current != null) {
                    val testRes = repository.testServerConnection(current)
                    testRes.fold(
                        onSuccess = { latency ->
                            _connectionState.value = ConnectionState.Connected(latency)
                        },
                        onFailure = { err ->
                            _connectionState.value = ConnectionState.Error(err.localizedMessage ?: "Connection timed out")
                        }
                    )
                    fetchDataOnce()
                }

                // 2. Terminal session refresh
                if (_terminalStatus.value !is TerminalStatus.Connected) {
                    restartTerminalSession()
                } else {
                    _terminalAction.tryEmit(TerminalAction.REDRAW)
                    sendTerminalInput("\u000C")
                    val cols = lastResizedCols.takeIf { it > 0 } ?: 80
                    val rows = lastResizedRows.takeIf { it > 0 } ?: 24
                    resizeTerminal(cols, rows, force = true)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun triggerTerminalRedraw() {
        _terminalAction.tryEmit(TerminalAction.REDRAW)
        sendTerminalInput("\u000C")
    }

    fun triggerTerminalReset() {
        _terminalAction.tryEmit(TerminalAction.RESET)
        resetTerminalShell()
    }

    fun triggerTerminalReload() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _terminalAction.tryEmit(TerminalAction.RELOAD)
                restartTerminalSession()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun triggerTerminalClear() {
        _terminalAction.tryEmit(TerminalAction.CLEAR)
        sendTerminalInput("\u000C")
    }

    /**
     * Resets a stuck or hung terminal session by sending SIGINT (^C) and EOF (^D) / exit
     * to terminate any running foreground process, clearing the local buffer, and
     * reconnecting so the fresh login/shell prompt appears immediately.
     */
    fun resetTerminalShell() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            _terminalStatus.value = TerminalStatus.Connecting
            try {
                // Send Ctrl+C to abort any running foreground process
                repository.writeTerminal("\u0003")
                delay(120L)
                // Send EOF (^D) + newline + exit to cleanly terminate the shell session and trigger getty login respawn
                repository.writeTerminal("\u0004\rexit\r")
                delay(250L)
            } catch (_: Exception) {}
            clearTerminalBuffer()
            restartTerminalSession()
            _isRefreshing.value = false
        }
    }

    /**
     * Sends reboot command to the DietPi node and enters a polling loop
     * waiting for the system to boot back up and restore connectivity.
     */
    fun rebootHost() {
        val server = _activeServer.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _powerOperationState.value = PowerOperationState.InProgress(
                action = "reboot",
                message = "Sending reboot command to ${server.nickname}..."
            )
            // Send both systemctl reboot and fallback reboot
            try {
                repository.writeTerminal("sudo systemctl reboot || sudo reboot\r")
            } catch (e: Exception) {
                Log.w("DietPiViewModel", "Terminal write for reboot failed: ${e.message}")
            }

            // Pause regular polling and terminal while the system reboots
            pollingJob?.cancel()
            pollingJob = null
            terminalJob?.cancel()
            terminalJob = null
            _connectionState.value = ConnectionState.Connecting

            // Give the OS 10 seconds to initiate shutdown and kill network interfaces
            for (countdown in 10 downTo 1) {
                _powerOperationState.value = PowerOperationState.Rebooting(
                    attempt = 0,
                    message = "Reboot command received. Node is restarting ($countdown s)..."
                )
                delay(1000L)
            }

            // Begin polling for node recovery
            var attempts = 0
            val maxAttempts = 60 // 60 * 2.5s = ~2.5 minutes timeout
            var reconnected = false

            while (isActive && attempts < maxAttempts) {
                attempts++
                _powerOperationState.value = PowerOperationState.Rebooting(
                    attempt = attempts,
                    message = "Waiting for ${server.nickname} to come back online (probe $attempts)..."
                )

                delay(2500L)
                val testRes = repository.testServerConnection(server)
                if (testRes.isSuccess) {
                    reconnected = true
                    break
                }
            }

            if (reconnected) {
                _powerOperationState.value = PowerOperationState.Completed(
                    message = "${server.nickname} rebooted successfully and is back online!"
                )
                startPolling()
                startTerminalSession()
            } else {
                _powerOperationState.value = PowerOperationState.Failed(
                    error = "Reboot timeout: ${server.nickname} did not respond after 2.5 minutes. Check physical power or network."
                )
                startPolling()
            }
        }
    }

    /**
     * Sends poweroff/shutdown command to the DietPi node.
     */
    fun poweroffHost() {
        val server = _activeServer.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _powerOperationState.value = PowerOperationState.InProgress(
                action = "poweroff",
                message = "Sending shutdown command to ${server.nickname}..."
            )
            try {
                repository.writeTerminal("sudo systemctl poweroff || sudo poweroff\r")
            } catch (e: Exception) {
                Log.w("DietPiViewModel", "Terminal write for poweroff failed: ${e.message}")
            }

            pollingJob?.cancel()
            pollingJob = null
            terminalJob?.cancel()
            terminalJob = null

            delay(3000L)
            _connectionState.value = ConnectionState.Idle
            _powerOperationState.value = PowerOperationState.Completed(
                message = "${server.nickname} has been instructed to shut down. Power cycle the hardware to turn it back on."
            )
        }
    }

    fun dismissPowerOperationState() {
        _powerOperationState.value = PowerOperationState.Idle
    }

    // ==========================================
    // File Browser & In-App Text Editor Operations
    // ==========================================

    fun loadDirectory(path: String = _currentDirectoryPath.value) {
        val server = _activeServer.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isFileBrowserLoading.value = true
            _fileBrowserError.value = null
            _currentDirectoryPath.value = path
            val result = repository.fetchFiles(path)
            if (result.isSuccess) {
                _directoryItems.value = result.getOrDefault(emptyList())
            } else {
                _fileBrowserError.value = result.exceptionOrNull()?.message ?: "Failed to list directory contents"
            }
            _isFileBrowserLoading.value = false
        }
    }

    fun navigateToDirectory(path: String, addToHistory: Boolean = true) {
        val current = _currentDirectoryPath.value
        if (addToHistory && current != path) {
            _directoryHistory.value = _directoryHistory.value + current
        }
        loadDirectory(path)
    }

    fun navigateBack(): Boolean {
        val history = _directoryHistory.value
        if (history.isNotEmpty()) {
            val previousPath = history.last()
            _directoryHistory.value = history.dropLast(1)
            loadDirectory(previousPath)
            return true
        }
        val current = _currentDirectoryPath.value.trimEnd('/')
        if (current.isNotEmpty() && current != "/") {
            val parent = current.substringBeforeLast('/', "")
            val target = if (parent.isEmpty()) "/" else parent
            loadDirectory(target)
            return true
        }
        return false
    }

    fun canNavigateBack(): Boolean {
        if (_directoryHistory.value.isNotEmpty()) return true
        val current = _currentDirectoryPath.value.trimEnd('/')
        return current.isNotEmpty() && current != "/"
    }

    fun navigateUp() {
        val current = _currentDirectoryPath.value.trimEnd('/')
        if (current.isEmpty() || current == "/") return
        val parent = current.substringBeforeLast('/', "")
        val target = if (parent.isEmpty()) "/" else parent
        navigateToDirectory(target, addToHistory = true)
    }

    fun toggleShowHiddenFiles() {
        _showHiddenFiles.value = !_showHiddenFiles.value
    }

    fun openTextFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _editingFilePath.value = path
            _isEditingFileLoading.value = true
            _editingFileError.value = null
            _editingFileContent.value = null
            val result = repository.fetchTextFile(path)
            if (result.isSuccess) {
                _editingFileContent.value = result.getOrNull() ?: ""
            } else {
                _editingFileError.value = result.exceptionOrNull()?.message ?: "Failed to open file"
            }
            _isEditingFileLoading.value = false
        }
    }

    fun closeTextFile() {
        _editingFilePath.value = null
        _editingFileContent.value = null
        _editingFileError.value = null
    }

    fun setWebUiZoomLevel(zoom: Int) {
        _webUiZoomLevel.value = zoom
        repository.setWebUiZoomLevel(zoom)
    }

    fun saveCurrentTextFile(content: String, onResult: (Boolean, String?) -> Unit) {
        val path = _editingFilePath.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isSavingFile.value = true
            val result = repository.saveTextFile(path, content)
            _isSavingFile.value = false
            if (result.isSuccess) {
                _editingFileContent.value = content
                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to save file"
                withContext(Dispatchers.Main) {
                    onResult(false, err)
                }
            }
        }
    }

    fun createFile(name: String, onResult: (Boolean, String?) -> Unit) {
        val parent = _currentDirectoryPath.value
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.createNewFile(parent, name)
            if (result.isSuccess) {
                loadDirectory(parent)
                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to create file"
                withContext(Dispatchers.Main) {
                    onResult(false, err)
                }
            }
        }
    }

    fun createFolder(name: String, onResult: (Boolean, String?) -> Unit) {
        val parent = _currentDirectoryPath.value
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.createNewFolder(parent, name)
            if (result.isSuccess) {
                loadDirectory(parent)
                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to create folder"
                withContext(Dispatchers.Main) {
                    onResult(false, err)
                }
            }
        }
    }

    fun renameItem(item: FileBrowserItem, newName: String, onResult: (Boolean, String?) -> Unit) {
        val parent = _currentDirectoryPath.value
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.renameFileOrFolder(item.path, newName)
            if (result.isSuccess) {
                loadDirectory(parent)
                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to rename"
                withContext(Dispatchers.Main) {
                    onResult(false, err)
                }
            }
        }
    }

    fun deleteItem(item: FileBrowserItem, onResult: (Boolean, String?) -> Unit) {
        val parent = _currentDirectoryPath.value
        viewModelScope.launch(Dispatchers.IO) {
            val result = if (item.isDirectory) {
                repository.deleteFolder(item.path)
            } else {
                repository.deleteFile(item.path)
            }
            if (result.isSuccess) {
                loadDirectory(parent)
                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to delete"
                withContext(Dispatchers.Main) {
                    onResult(false, err)
                }
            }
        }
    }

    fun downloadFile(item: FileBrowserItem, onResult: (Boolean, ByteArray?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.downloadFileBytes(item.path)
            if (result.isSuccess) {
                val bytes = result.getOrNull()
                withContext(Dispatchers.Main) {
                    onResult(true, bytes, null)
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to download"
                withContext(Dispatchers.Main) {
                    onResult(false, null, err)
                }
            }
        }
    }

    fun uploadFile(name: String, bytes: ByteArray, onResult: (Boolean, String?) -> Unit) {
        val parent = _currentDirectoryPath.value
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.uploadFile(parent, name, bytes)
            if (result.isSuccess) {
                loadDirectory(parent)
                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to upload file"
                withContext(Dispatchers.Main) {
                    onResult(false, err)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.discoveryManager.stopDiscovery()
        pollingJob?.cancel()
        terminalJob?.cancel()
    }

    companion object {
        fun provideFactory(repository: DietPiRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DietPiViewModel(repository) as T
                }
            }
    }
}
