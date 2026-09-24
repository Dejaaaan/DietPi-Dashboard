package com.example.data.repository

import android.content.Context
import com.example.data.catalog.DietPiSoftwareCatalog
import com.example.data.local.ServerDao
import com.example.data.local.ServerEntity
import com.example.data.model.*
import com.example.data.remote.DietPiClient
import com.example.data.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DietPiRepository(
    private val serverDao: ServerDao,
    private val context: Context
) {

    val allServers: Flow<List<ServerEntity>> = serverDao.getAllServers().map { list ->
        list.map { it.copy(password = CryptoManager.decrypt(it.password)) }
    }

    val discoveryManager = com.example.data.discovery.NetworkDiscoveryManager(context)

    private var activeClient: DietPiClient? = null
    private var currentServer: ServerEntity? = null

    fun setActiveServer(server: ServerEntity?) {
        currentServer = server
        if (server == null) {
            activeClient = null
        } else {
            val existing = activeClient
            if (existing == null || existing.server.id != server.id || existing.server.baseUrl != server.baseUrl || existing.server.password != server.password) {
                activeClient = DietPiClient(server)
            }
        }
    }

    fun getActiveServer(): ServerEntity? = currentServer

    suspend fun testServerConnection(server: ServerEntity): Result<Long> {
        val client = if (activeClient?.server?.id == server.id) {
            activeClient!!
        } else {
            val newClient = DietPiClient(server)
            if (currentServer?.id == server.id) {
                activeClient = newClient
            }
            newClient
        }
        return client.testConnection()
    }

    suspend fun fetchSystemStats(): Result<SystemStats> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.fetchSystemStats()
    }

    suspend fun fetchHostInfo(): Result<HostInfo> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.fetchHostInfo()
    }

    suspend fun fetchProcesses(): Result<List<ProcessItem>> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.fetchProcesses()
    }

    suspend fun sendProcessSignal(pid: Int, signal: ProcessSignal): Result<Boolean> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.sendProcessSignal(pid, signal)
    }

    suspend fun fetchServices(): Result<List<ServiceItem>> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.fetchServices()
    }

    suspend fun fetchSoftware(): Result<List<SoftwareItem>> {
        // Base catalog containing 210 items with docs, homepages, images, and categories
        val baseCatalog = DietPiSoftwareCatalog.getSoftwareList(context)
        val client = activeClient

        if (client == null) {
            return Result.success(baseCatalog)
        }

        val remoteRes = client.fetchSoftware()
        if (remoteRes.isFailure) {
            return Result.success(baseCatalog)
        }

        val remoteList = remoteRes.getOrDefault(emptyList())
        val installedIds = remoteList.filter { it.isInstalled }.map { it.id }.toSet()
        val installedNames = remoteList.filter { it.isInstalled }.map { it.name.trim().lowercase() }.toSet()

        val merged = baseCatalog.map { item ->
            val isInst = (item.id in installedIds) || (item.name.trim().lowercase() in installedNames)
            item.copy(isInstalled = isInst)
        }

        return Result.success(merged)
    }

    suspend fun toggleSoftware(id: Int, isInstalled: Boolean = false): Result<Boolean> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.toggleSoftware(id, isInstalled)
    }

    suspend fun fetchFiles(path: String): Result<List<FileBrowserItem>> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.fetchFiles(path)
    }

    suspend fun fetchTextFile(path: String): Result<String> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.fetchTextFile(path)
    }

    suspend fun saveTextFile(path: String, content: String): Result<Boolean> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.saveTextFile(path, content)
    }

    suspend fun createNewFile(parentPath: String, name: String): Result<Boolean> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.createNewFile(parentPath, name)
    }

    suspend fun createNewFolder(parentPath: String, name: String): Result<Boolean> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.createNewFolder(parentPath, name)
    }

    suspend fun renameFileOrFolder(path: String, newName: String): Result<Boolean> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.renameFileOrFolder(path, newName)
    }

    suspend fun deleteFile(path: String): Result<Boolean> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.deleteFile(path)
    }

    suspend fun deleteFolder(path: String): Result<Boolean> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.deleteFolder(path)
    }

    suspend fun downloadFileBytes(path: String): Result<ByteArray> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.downloadFileBytes(path)
    }

    suspend fun uploadFile(parentPath: String, name: String, bytes: ByteArray): Result<Boolean> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.uploadFile(parentPath, name, bytes)
    }

    fun openTerminalStream(): Flow<ByteArray>? {
        return activeClient?.openTerminalStream()
    }

    suspend fun writeTerminal(input: String): Result<Unit> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.writeTerminal(input)
    }

    suspend fun resizeTerminal(cols: Int, rows: Int): Result<Unit> {
        val client = activeClient ?: return Result.failure(Exception("No DietPi node connected"))
        return client.resizeTerminal(cols, rows)
    }

    fun getActiveServerCookies(): List<okhttp3.Cookie> {
        return activeClient?.getCookies() ?: emptyList()
    }

    // Server management
    suspend fun insertServer(server: ServerEntity): Long {
        if (server.isDefault) {
            serverDao.clearDefaultFlags()
        }
        val secured = server.copy(password = CryptoManager.encrypt(server.password))
        return serverDao.insertServer(secured)
    }

    suspend fun updateServer(server: ServerEntity) {
        if (server.isDefault) {
            serverDao.clearDefaultFlags()
        }
        val secured = server.copy(password = CryptoManager.encrypt(server.password))
        serverDao.updateServer(secured)
        if (currentServer?.id == server.id) {
            setActiveServer(server)
        }
    }

    suspend fun deleteServer(server: ServerEntity) {
        serverDao.deleteServer(server)
        if (currentServer?.id == server.id) {
            setActiveServer(null)
        }
    }

    suspend fun setDefaultServer(id: Long) {
        serverDao.clearDefaultFlags()
        serverDao.setDefaultServer(id)
    }

    suspend fun getDefaultServer(): ServerEntity? {
        return serverDao.getDefaultServer()?.let {
            it.copy(password = CryptoManager.decrypt(it.password))
        }
    }

    suspend fun getServerById(id: Long): ServerEntity? {
        return serverDao.getServerById(id)?.let {
            it.copy(password = CryptoManager.decrypt(it.password))
        }
    }

    fun getWebUiZoomLevel(): Int {
        val prefs = context.getSharedPreferences("dietpi_ui_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("web_ui_zoom_level", 50)
    }

    fun setWebUiZoomLevel(zoom: Int) {
        val prefs = context.getSharedPreferences("dietpi_ui_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("web_ui_zoom_level", zoom).apply()
    }
}
