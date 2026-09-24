package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ServerEntity
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectorSheet(
    servers: List<ServerEntity>,
    activeServerId: Long?,
    onSelectServer: (ServerEntity) -> Unit,
    onAddServer: (ServerEntity) -> Unit,
    onUpdateServer: (ServerEntity) -> Unit,
    onDeleteServer: (ServerEntity) -> Unit,
    onTestConnection: suspend (ServerEntity) -> Result<Long>,
    onDismiss: () -> Unit,
    serverToEditInitially: ServerEntity? = null,
    isScanningNetwork: Boolean = false,
    discoveredNodes: List<com.example.data.discovery.DiscoveredDietPi> = emptyList(),
    scanStatus: String = "Idle",
    candidateSubnets: List<String> = emptyList(),
    onStartScan: (customSubnet: String?) -> Unit = {},
    onStopScan: () -> Unit = {}
) {
    var serverToEdit by remember { mutableStateOf<ServerEntity?>(serverToEditInitially) }
    var showAddDialog by remember { mutableStateOf(false) }
    var initialAddHost by remember { mutableStateOf<String?>(null) }
    var initialAddPort by remember { mutableStateOf<Int?>(null) }
    var initialAddUseHttps by remember { mutableStateOf<Boolean?>(null) }
    var initialAddNickname by remember { mutableStateOf<String?>(null) }
    var showCustomSubnetInput by remember { mutableStateOf(false) }
    var customSubnetText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = {
            onStopScan()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DietPi Nodes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select active server or auto-discover DietPi nodes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (isScanningNetwork) onStopScan() else onStartScan(null)
                            },
                            modifier = Modifier.testTag("scan_network_button")
                        ) {
                            if (isScanningNetwork) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = "Scan Network for DietPi",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                initialAddHost = null
                                showAddDialog = true
                            },
                            modifier = Modifier.testTag("add_server_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Add Server",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // Auto-discovery suggestions section
            if (isScanningNetwork || showCustomSubnetInput || candidateSubnets.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WifiTethering,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isScanningNetwork) "Searching Network..." else "Discovered Nodes (${discoveredNodes.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { showCustomSubnetInput = !showCustomSubnetInput },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Configure Subnet",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    if (isScanningNetwork) {
                                        TextButton(
                                            onClick = onStopScan,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text("Stop", style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = { onStartScan(null) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text("Rescan", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            if (isScanningNetwork) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Subnet chips or custom subnet input
                            if (showCustomSubnetInput) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = customSubnetText,
                                        onValueChange = { customSubnetText = it },
                                        label = { Text("Subnet (e.g. 192.168.1)") },
                                        placeholder = { Text("192.168.1") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        textStyle = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            onStartScan(customSubnetText.ifBlank { null })
                                        },
                                        enabled = !isScanningNetwork
                                    ) {
                                        Text("Scan", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else if (candidateSubnets.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Subnets:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    candidateSubnets.take(3).forEach { subnet ->
                                        SuggestionChip(
                                            onClick = { onStartScan(subnet) },
                                            label = {
                                                Text(
                                                    "$subnet.x",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                                )
                                            },
                                            modifier = Modifier.height(26.dp)
                                        )
                                    }
                                }
                            }

                            // Status line
                            Text(
                                text = scanStatus,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                        }
                    }
                }
            }

            if (discoveredNodes.isNotEmpty()) {
                item {
                    Text(
                        text = "DISCOVERED NODES (${discoveredNodes.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                items(discoveredNodes, key = { "discovered_${it.host}_${it.port}" }) { node ->
                    val matched = servers.find { it.host.equals(node.host, ignoreCase = true) }
                    val isAlreadySaved = matched != null
                    val isSelected = matched != null && matched.id == activeServerId

                    val scheme = if (node.useHttps) "https" else "http"
                    val formattedUrl = "$scheme://${node.host}:${node.port}"

                    val badges = buildList {
                        if (node.useHttps) add("HTTPS") else add("HTTP")
                        if (!node.isDashboardReady) add("SSH Port 22")
                        if (isAlreadySaved) add("Saved")
                        if (isSelected) add("Active")
                    }

                    ServerCardItem(
                        title = if (isAlreadySaved && matched != null) matched.nickname else node.name,
                        subtitle = formattedUrl,
                        isSelected = isSelected,
                        badges = badges,
                        icon = if (node.isDashboardReady) Icons.Default.Dns else Icons.Default.Terminal,
                        statusNote = if (isAlreadySaved) (if (isSelected) "Active node" else "Saved node") else node.discoveryMethod,
                        onClick = {
                            if (matched != null) {
                                onSelectServer(matched)
                                onDismiss()
                            } else {
                                initialAddHost = node.host
                                initialAddPort = node.port
                                initialAddUseHttps = node.useHttps
                                initialAddNickname = node.name
                                showAddDialog = true
                            }
                        },
                        modifier = Modifier.testTag("discovered_card_${node.host}")
                    ) {
                        if (isAlreadySaved && matched != null) {
                            IconButton(
                                onClick = { serverToEdit = matched },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("edit_server_button_${matched.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Server",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (!isSelected) {
                                Button(
                                    onClick = {
                                        onSelectServer(matched)
                                        onDismiss()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Connect", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            // Server not yet added: only show Add button
                            Button(
                                onClick = {
                                    initialAddHost = node.host
                                    initialAddPort = node.port
                                    initialAddUseHttps = node.useHttps
                                    initialAddNickname = node.name
                                    showAddDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("add_discovered_${node.host}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Server", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "SAVED SERVERS (${servers.size})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            }

            if (servers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No saved DietPi servers yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.testTag("add_first_server_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add DietPi Server")
                            }
                        }
                    }
                }
            } else {
                items(servers, key = { "saved_${it.id}" }) { server ->
                    val isSelected = server.id == activeServerId
                    val badges = buildList {
                        if (server.useHttps) add("HTTPS") else add("HTTP")
                        if (server.isDefault) add("Default")
                        if (isSelected) add("Active")
                    }

                    ServerCardItem(
                        title = server.nickname,
                        subtitle = server.baseUrl,
                        isSelected = isSelected,
                        badges = badges,
                        icon = Icons.Default.Dns,
                        statusNote = if (isSelected) "Active node" else if (server.isDefault) "Default node" else null,
                        onClick = {
                            onSelectServer(server)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("server_card_${server.id}")
                    ) {
                        // Edit button
                        IconButton(
                            onClick = { serverToEdit = server },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("edit_server_button_${server.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Server",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Delete button
                        IconButton(
                            onClick = { onDeleteServer(server) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("delete_server_button_${server.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Server",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (!isSelected) {
                            Button(
                                onClick = {
                                    onSelectServer(server)
                                    onDismiss()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Connect", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showAddDialog) {
        ServerFormDialog(
            server = null,
            initialHost = initialAddHost,
            initialPort = initialAddPort,
            initialUseHttps = initialAddUseHttps,
            initialNickname = initialAddNickname,
            onDismiss = {
                showAddDialog = false
                initialAddHost = null
                initialAddPort = null
                initialAddUseHttps = null
                initialAddNickname = null
            },
            onSave = { newServer ->
                onAddServer(newServer)
                showAddDialog = false
                initialAddHost = null
                initialAddPort = null
                initialAddUseHttps = null
                initialAddNickname = null
            },
            onTestConnection = onTestConnection
        )
    }

    serverToEdit?.let { existing ->
        ServerFormDialog(
            server = existing,
            onDismiss = { serverToEdit = null },
            onSave = { updatedServer ->
                onUpdateServer(updatedServer)
                serverToEdit = null
            },
            onTestConnection = onTestConnection
        )
    }
}

@Composable
fun ServerFormDialog(
    server: ServerEntity?,
    initialHost: String? = null,
    initialPort: Int? = null,
    initialUseHttps: Boolean? = null,
    initialNickname: String? = null,
    onDismiss: () -> Unit,
    onSave: (ServerEntity) -> Unit,
    onTestConnection: suspend (ServerEntity) -> Result<Long>
) {
    val isEditing = server != null

    var nickname by remember { mutableStateOf(server?.nickname ?: (initialNickname ?: (if (!initialHost.isNullOrBlank()) "DietPi ($initialHost)" else "DietPi Server"))) }
    var hostInput by remember { mutableStateOf(server?.host ?: (initialHost ?: "192.168.1.100")) }
    var portInput by remember { mutableStateOf(server?.port?.toString() ?: (initialPort?.toString() ?: "5252")) }
    var password by remember { mutableStateOf(server?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var useHttps by remember { mutableStateOf(server?.useHttps ?: (initialUseHttps ?: false)) }
    var setAsDefault by remember { mutableStateOf(server?.isDefault ?: true) }

    // Test connection state
    val coroutineScope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<Long>?>(null) }

    fun buildCandidate(): ServerEntity {
        var cleanHost = hostInput.trim()
        var detectedHttps = useHttps
        var detectedPort = portInput.toIntOrNull() ?: 5252

        // Smart parser if user pasted full URL (e.g. https://192.168.1.50:5252/)
        if (cleanHost.startsWith("https://", ignoreCase = true)) {
            detectedHttps = true
            cleanHost = cleanHost.substringAfter("://")
        } else if (cleanHost.startsWith("http://", ignoreCase = true)) {
            cleanHost = cleanHost.substringAfter("://")
        }

        cleanHost = cleanHost.trimEnd('/')

        if (cleanHost.contains(":")) {
            val portPart = cleanHost.substringAfter(":")
            val parsedPort = portPart.toIntOrNull()
            if (parsedPort != null) {
                detectedPort = parsedPort
            }
            cleanHost = cleanHost.substringBefore(":")
        }

        return ServerEntity(
            id = server?.id ?: 0L,
            nickname = nickname.trim().ifEmpty { "DietPi" },
            host = cleanHost.ifEmpty { "192.168.1.100" },
            port = detectedPort,
            useHttps = detectedHttps,
            password = password,
            isDefault = setAsDefault
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Server Configuration" else "Add DietPi Server",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Server Nickname") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_nickname_input")
                )

                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { input ->
                        var cleaned = input.trim()
                        if (cleaned.startsWith("https://", ignoreCase = true)) {
                            useHttps = true
                            cleaned = cleaned.substringAfter("://")
                        } else if (cleaned.startsWith("http://", ignoreCase = true)) {
                            useHttps = false
                            cleaned = cleaned.substringAfter("://")
                        }
                        if (cleaned.contains("/")) {
                            cleaned = cleaned.substringBefore("/")
                        }
                        if (cleaned.contains(":")) {
                            val portCandidate = cleaned.substringAfter(":")
                            if (portCandidate.toIntOrNull() != null) {
                                portInput = portCandidate
                                cleaned = cleaned.substringBefore(":")
                            }
                        }
                        hostInput = cleaned
                    },
                    label = { Text("Host IP / Domain") },
                    placeholder = { Text("192.168.1.50 or dietpi.local") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_host_input")
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_port_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    placeholder = { Text("Leave blank if no auth required") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = "Password Security")
                    },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val desc = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(image, contentDescription = desc)
                        }
                    },
                    supportingText = {
                        Text(
                            "Encrypted with Android KeyStore AES-256. Leave empty if unauthenticated.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_password_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Use HTTPS (SSL)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Default DietPi-Dashboard uses HTTP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useHttps,
                        onCheckedChange = { useHttps = it },
                        modifier = Modifier.testTag("server_https_switch")
                    )
                }

                // In-dialog Test Connection button and feedback
                OutlinedButton(
                    onClick = {
                        val candidate = buildCandidate()
                        isTesting = true
                        testResult = null
                        coroutineScope.launch {
                            val res = onTestConnection(candidate)
                            isTesting = false
                            testResult = res
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_connection_button"),
                    enabled = !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing Connection...")
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection Now")
                    }
                }

                AnimatedVisibility(visible = testResult != null) {
                    testResult?.fold(
                        onSuccess = { latency ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = com.example.ui.theme.DietPiGreenContainer.copy(alpha = 0.4f),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(DietPiGreenPrimary)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DietPiGreenLight)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Success! Responded in ${latency}ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = DietPiGreenLight
                                    )
                                }
                            }
                        },
                        onFailure = { err ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Connection Failed",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = err.localizedMessage ?: "Could not reach server.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val candidate = buildCandidate()
                    onSave(candidate)
                },
                modifier = Modifier.testTag("save_server_button")
            ) {
                Text(if (isEditing) "Save Changes" else "Add Server")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ServerCardItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    badges: List<String> = emptyList(),
    icon: ImageVector = Icons.Default.Dns,
    statusNote: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: @Composable RowScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top row: Avatar + Title/Subtitle/Badges + Active Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DietPiGreenContainer,
                                border = BorderStroke(1.dp, DietPiGreenPrimary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(DietPiGreenPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = DietPiOnGreenContainer
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val chipBadges = badges.filter { it != "Active" }
                    if (chipBadges.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            chipBadges.forEach { badge ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = when (badge) {
                                        "Saved" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                                        "HTTPS" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                        "Default" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = badge,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = when (badge) {
                                            "Saved" -> MaterialTheme.colorScheme.onSecondaryContainer
                                            "HTTPS" -> MaterialTheme.colorScheme.onPrimaryContainer
                                            "Default" -> MaterialTheme.colorScheme.onTertiaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(
                thickness = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!statusNote.isNullOrBlank()) {
                    Text(
                        text = statusNote,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    trailingActions()
                }
            }
        }
    }
}
