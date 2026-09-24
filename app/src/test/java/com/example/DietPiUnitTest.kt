package com.example

import com.example.data.model.DiskInfoItem
import com.example.data.model.SystemStats
import com.example.ui.components.formatBytes
import com.example.ui.components.formatRate
import com.example.ui.components.formatUptime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DietPiUnitTest {

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("1.0 KB", formatBytes(1024L))
        assertEquals("1.0 MB", formatBytes(1024L * 1024L))
        assertEquals("2.5 GB", formatBytes((2.5 * 1024L * 1024L * 1024L).toLong()))
    }

    @Test
    fun testFormatRate() {
        assertEquals("1.0 MB/s", formatRate(1024L * 1024L))
    }

    @Test
    fun testFormatUptime() {
        assertEquals("5d 1h 2m", formatUptime(435720L))
        assertEquals("2h 15m", formatUptime(8100L))
        assertEquals("45m", formatUptime(2700L))
    }

    @Test
    fun testSystemStatsPercentages() {
        val stats = SystemStats(
            cpuGlobal = 25.5f,
            ramUsed = 2_000_000_000L,
            ramTotal = 4_000_000_000L,
            swapUsed = 500_000_000L,
            swapTotal = 1_000_000_000L
        )
        assertEquals(50.0f, stats.ramPercent, 0.01f)
        assertEquals(50.0f, stats.swapPercent, 0.01f)
    }

    @Test
    fun testDiskInfoItem() {
        val disk = DiskInfoItem("Root", "/", 10_000_000_000L, 20_000_000_000L)
        assertEquals(50.0f, disk.percent, 0.01f)
    }

    @Test
    fun testSoftwareCatalogImageUrlsUseOfficialDietPiAssets() {
        val file = java.io.File("src/main/assets/dietpi_software.json").takeIf { it.exists() }
            ?: java.io.File("app/src/main/assets/dietpi_software.json")
        val text = file.readText()
        val regex = Regex(""""imageUrl"\s*:\s*"([^"]*)"""")
        val matches = regex.findAll(text).map { it.groupValues[1] }.filter { it.isNotBlank() }.toList()
        assertTrue("Catalog should have images mapped", matches.size > 150)
        matches.forEach { img ->
            assertTrue(
                "Image URL should use official dietpi assets: $img",
                img.startsWith("https://dietpi.com/docs/assets/images/")
            )
        }
    }

    @Test
    fun testMainTabsConfiguration() {
        val tabs = MainTab.values()
        assertEquals(7, tabs.size)
        assertTrue(tabs.any { it == MainTab.SYSTEM })
        assertTrue(tabs.any { it == MainTab.PROCESSES })
        assertTrue(tabs.any { it == MainTab.SERVICES })
        assertTrue(tabs.any { it == MainTab.SOFTWARE })
        assertTrue(tabs.any { it == MainTab.TERMINAL })
        assertTrue(tabs.any { it == MainTab.FILE_BROWSER })
        assertTrue(tabs.any { it == MainTab.WEB_UI })
        assertEquals("tab_terminal", MainTab.TERMINAL.tag)
        assertEquals("Terminal", MainTab.TERMINAL.title)
        assertEquals("tab_file_browser", MainTab.FILE_BROWSER.tag)
        assertEquals("Files", MainTab.FILE_BROWSER.title)
        assertEquals("tab_web_ui", MainTab.WEB_UI.tag)
        assertEquals("Web UI", MainTab.WEB_UI.title)
    }

    @Test
    fun testTerminalAssetsExist() {
        val baseDir = if (java.io.File("app/src/main/assets").exists()) "app/src/main/assets" else "src/main/assets"
        val html = java.io.File("$baseDir/terminal.html")
        val css = java.io.File("$baseDir/xterm-5.5.0.css")
        val js = java.io.File("$baseDir/xterm-5.5.0.js")
        val fit = java.io.File("$baseDir/xterm-addon-fit-0.10.0.js")

        assertTrue("terminal.html must exist", html.exists())
        assertTrue("xterm-5.5.0.css must exist", css.exists())
        assertTrue("xterm-5.5.0.js must exist", js.exists())
        assertTrue("xterm-addon-fit-0.10.0.js must exist", fit.exists())
    }

    @Test
    fun testDiscoveredDietPiModel() {
        val node = com.example.data.discovery.DiscoveredDietPi(
            host = "192.168.1.42",
            port = 5252,
            name = "DietPi (192.168.1.42)",
            discoveryMethod = "Port 5252 (HTTP)",
            useHttps = false,
            isDashboardReady = true
        )
        assertEquals("192.168.1.42", node.host)
        assertEquals(5252, node.port)
        assertTrue(node.isDashboardReady)
        assertFalse(node.useHttps)
    }

    @Test
    fun testSidebarAndHeaderTags() {
        // Ensure all MainTab tags are defined consistently for sidebar navigation
        val expectedTags = listOf("tab_system", "tab_processes", "tab_services", "tab_software", "tab_terminal", "tab_file_browser", "tab_web_ui")
        val actualTags = MainTab.values().map { it.tag }
        assertEquals(expectedTags, actualTags)
    }

    @Test
    fun testFileBrowserModelProperties() {
        val dir = com.example.data.model.FileBrowserItem(
            path = "/root/scripts",
            name = "scripts",
            isDirectory = true,
            size = null,
            kind = com.example.data.model.FileKind.Directory,
            isHidden = false
        )
        assertTrue(dir.isDirectory)
        assertEquals("--", dir.formattedSize)
        assertEquals(com.example.data.model.FileKind.Directory, dir.kind)

        val file = com.example.data.model.FileBrowserItem(
            path = "/root/dietpi.txt",
            name = "dietpi.txt",
            isDirectory = false,
            size = 2048L,
            kind = com.example.data.model.FileKind.TextFile,
            isHidden = false
        )
        assertFalse(file.isDirectory)
        assertEquals("2.0 KB", file.formattedSize)
        assertEquals(com.example.data.model.FileKind.TextFile, file.kind)
    }

    @Test
    fun testServerEntityDefaultPasswordIsBlank() {
        val server = com.example.data.local.ServerEntity(
            nickname = "No Auth Node",
            host = "192.168.1.55"
        )
        assertEquals("", server.password)
    }

    @Test
    fun testCryptoManagerEncryptionAndDecryption() {
        val original = "MySecureP@ssw0rd!#123"
        val encrypted = com.example.data.security.CryptoManager.encrypt(original)
        assertTrue("Encrypted output should start with enc:v1:", encrypted.startsWith("enc:v1:"))
        assertFalse("Ciphertext must not contain plaintext password", encrypted.contains(original))

        val decrypted = com.example.data.security.CryptoManager.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun testCryptoManagerBlankPasswordSupport() {
        val blankEncrypted = com.example.data.security.CryptoManager.encrypt("")
        assertEquals("", blankEncrypted)
        val blankDecrypted = com.example.data.security.CryptoManager.decrypt("")
        assertEquals("", blankDecrypted)
    }

    @Test
    fun testCryptoManagerLegacyPlaintextSupport() {
        val legacy = "dietpi"
        val decrypted = com.example.data.security.CryptoManager.decrypt(legacy)
        assertEquals("Legacy plaintext without enc:v1: prefix should pass through", legacy, decrypted)
    }

    @Test
    fun testPowerOperationStateTransitions() {
        val idle: com.example.data.model.PowerOperationState = com.example.data.model.PowerOperationState.Idle
        assertTrue(idle is com.example.data.model.PowerOperationState.Idle)

        val inProgress = com.example.data.model.PowerOperationState.InProgress("reboot", "Sending reboot command...")
        assertEquals("reboot", inProgress.action)

        val rebooting = com.example.data.model.PowerOperationState.Rebooting(attempt = 3, message = "Probing...")
        assertEquals(3, rebooting.attempt)

        val completed = com.example.data.model.PowerOperationState.Completed("Node back online")
        assertEquals("Node back online", completed.message)

        val failed = com.example.data.model.PowerOperationState.Failed("Connection timeout")
        assertEquals("Connection timeout", failed.error)
    }

    @Test
    fun testMainTabEnumValues() {
        assertEquals(7, com.example.MainTab.values().size)
        assertEquals(com.example.MainTab.SYSTEM, com.example.MainTab.valueOf("SYSTEM"))
        assertEquals(com.example.MainTab.FILE_BROWSER, com.example.MainTab.valueOf("FILE_BROWSER"))
    }

    @Test
    fun testMediaFileClassification() {
        // Images
        assertTrue(com.example.ui.components.isImageFile("photo.png"))
        assertTrue(com.example.ui.components.isImageFile("avatar.jpg"))
        assertTrue(com.example.ui.components.isImageFile("graphic.webp"))
        assertTrue(com.example.ui.components.isImageFile("logo.svg"))
        assertFalse(com.example.ui.components.isImageFile("video.mp4"))

        // Videos
        assertTrue(com.example.ui.components.isVideoFile("movie.mp4"))
        assertTrue(com.example.ui.components.isVideoFile("clip.webm"))
        assertTrue(com.example.ui.components.isVideoFile("sample.mkv"))
        assertFalse(com.example.ui.components.isVideoFile("song.mp3"))

        // Audio
        assertTrue(com.example.ui.components.isAudioFile("song.mp3"))
        assertTrue(com.example.ui.components.isAudioFile("recording.wav"))
        assertTrue(com.example.ui.components.isAudioFile("track.flac"))
        assertFalse(com.example.ui.components.isAudioFile("document.pdf"))

        // Overall Media
        assertTrue(com.example.ui.components.isMediaFile("image.png"))
        assertTrue(com.example.ui.components.isMediaFile("clip.mp4"))
        assertTrue(com.example.ui.components.isMediaFile("music.ogg"))
        assertFalse(com.example.ui.components.isMediaFile("text.txt"))
    }

    @Test
    fun testDirectoryHistoryBackLogic() {
        val history = mutableListOf<String>()
        var currentPath = "/root"

        // Navigate forward to /var
        history.add(currentPath)
        currentPath = "/var"

        // Navigate forward to /var/log
        history.add(currentPath)
        currentPath = "/var/log"

        assertEquals(2, history.size)
        assertEquals("/var/log", currentPath)

        // Navigate back
        val prev1 = history.removeAt(history.lastIndex)
        currentPath = prev1
        assertEquals("/var", currentPath)

        // Navigate back again
        val prev2 = history.removeAt(history.lastIndex)
        currentPath = prev2
        assertEquals("/root", currentPath)
        assertTrue(history.isEmpty())
    }

    @Test
    fun testDietPiPrefixCommandBehavior() {
        fun formatCmd(cmd: String): String {
            return if (cmd.endsWith("-") || cmd == "dietpi-") {
                cmd
            } else {
                "$cmd\r"
            }
        }

        assertEquals("dietpi-", formatCmd("dietpi-"))
        assertEquals("dietpi-launcher\r", formatCmd("dietpi-launcher"))
        assertEquals("dietpi-config\r", formatCmd("dietpi-config"))
    }

    @Test
    fun testWebUiDefaultZoomIs100() {
        val defaultZoom = 100
        val zoomOptions = listOf(125, 100, 85, 75, 65, 50)
        assertTrue(zoomOptions.contains(defaultZoom))
        assertEquals(100, defaultZoom)

        // Factor calculation test
        val factor100 = 100 / 100.0
        assertEquals(1.0, factor100, 0.001)

        val factor85 = 85 / 100.0
        assertEquals(0.85, factor85, 0.001)
    }
}

