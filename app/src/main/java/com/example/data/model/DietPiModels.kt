package com.example.data.model

data class MetricPoint(
    val timestamp: Long = System.currentTimeMillis(),
    val value: Float = 0f
)

data class TemperatureItem(
    val label: String,
    val tempCelsius: Float
) {
    val tempFahrenheit: Float get() = tempCelsius * 9f / 5f + 32f
    val isElevated: Boolean get() = tempCelsius > 65f
    val isCritical: Boolean get() = tempCelsius > 80f
}

data class SystemStats(
    val cpuGlobal: Float = 0f,
    val cpuCores: List<Float> = emptyList(),
    val cpuTemp: Float? = null,
    val temperatures: List<TemperatureItem> = emptyList(),
    val ramUsed: Long = 0L,
    val ramTotal: Long = 1L,
    val swapUsed: Long = 0L,
    val swapTotal: Long = 1L,
    val disks: List<DiskInfoItem> = emptyList(),
    val netSent: Long = 0L,
    val netRecv: Long = 0L,
    val netSentRate: Long = 0L, // bytes/sec
    val netRecvRate: Long = 0L, // bytes/sec
    val historyCpu: List<MetricPoint> = emptyList(),
    val historyRam: List<MetricPoint> = emptyList(),
    val historyTemp: List<MetricPoint> = emptyList(),
    val historyNetSent: List<MetricPoint> = emptyList(),
    val historyNetRecv: List<MetricPoint> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    val ramPercent: Float
        get() = if (ramTotal > 0) (ramUsed.toFloat() / ramTotal.toFloat()) * 100f else 0f

    val swapPercent: Float
        get() = if (swapTotal > 0) (swapUsed.toFloat() / swapTotal.toFloat()) * 100f else 0f
}

data class DiskInfoItem(
    val name: String,
    val mountPoint: String,
    val used: Long,
    val total: Long
) {
    val percent: Float
        get() = if (total > 0) (used.toFloat() / total.toFloat()) * 100f else 0f
}

data class HostInfo(
    val hostname: String = "DietPi",
    val nic: String = "eth0",
    val arch: String = "aarch64",
    val uptimeSeconds: Long = 0L,
    val kernel: String = "Linux 6.6",
    val osVersion: String = "Debian 12 (bookworm)",
    val dietPiVersion: String = "v9.6.1",
    val packageCount: Int = 348
)

data class ProcessItem(
    val pid: Int,
    val name: String,
    val cpu: Float,
    val mem: Long,
    val status: ProcessStatus
)

enum class ProcessStatus {
    Running,
    Sleeping,
    Paused,
    Other
}

enum class ProcessSignal(val wireName: String, val displayName: String, val signalCode: String) {
    TERM("term", "Terminate (SIGTERM)", "15"),
    KILL("kill", "Force Kill (SIGKILL)", "9"),
    PAUSE("pause", "Pause (SIGSTOP)", "19"),
    RESUME("resume", "Resume (SIGCONT)", "18")
}

data class ServiceItem(
    val name: String,
    val status: ServiceStatus,
    val startTime: String,
    val errorLog: String = ""
)

enum class ServiceStatus {
    Active,
    Inactive,
    Failed,
    Unknown
}

data class SoftwareItem(
    val id: Int,
    val name: String,
    val desc: String,
    val category: String = "General Software",
    val homepage: String = "",
    val imageUrl: String = "",
    val deps: String = "",
    val docs: String = "",
    val isInstalled: Boolean = false
)

enum class FileKind(val displayName: String) {
    Directory("Folder"),
    TextFile("Text File"),
    BinaryFile("Binary File"),
    Special("Special")
}

data class FileBrowserItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val kind: FileKind = if (isDirectory) FileKind.Directory else FileKind.BinaryFile,
    val isHidden: Boolean = name.startsWith(".")
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "--"
            val bytes = size ?: return "--"
            if (bytes < 1024) return "$bytes B"
            val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
            val pre = "KMGTPE"[exp - 1]
            return String.format(java.util.Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
        }
}

sealed interface ConnectionState {
    object Idle : ConnectionState
    object Connecting : ConnectionState
    data class Connected(val latencyMs: Long) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class AutoRetryState(
    val secondsRemaining: Int = 0,
    val attempt: Int = 0,
    val maxAttempts: Int = 5,
    val isRetryingNow: Boolean = false,
    val isPaused: Boolean = false
)

sealed interface TerminalStatus {
    object Disconnected : TerminalStatus
    object Connecting : TerminalStatus
    data class Connected(val info: String = "DietPi PTY") : TerminalStatus
    data class Error(val message: String) : TerminalStatus
}

sealed interface PowerOperationState {
    object Idle : PowerOperationState
    data class InProgress(val action: String, val message: String) : PowerOperationState
    data class Rebooting(val attempt: Int = 0, val message: String = "Rebooting DietPi node...") : PowerOperationState
    data class Completed(val message: String) : PowerOperationState
    data class Failed(val error: String) : PowerOperationState
}

