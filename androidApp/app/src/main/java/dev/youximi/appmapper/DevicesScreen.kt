package dev.youximi.appmapper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.youximi.appmapper.data.PairedComputer
import dev.youximi.appmapper.data.PairingState
import dev.youximi.appmapper.service.ConnectionPhase
import dev.youximi.appmapper.service.ConnectionState
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevicesScreen(
    pairing: PairingState,
    connection: ConnectionState,
    error: String?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onConnect: (String) -> Unit,
    onForget: (String) -> Unit,
) {
    var forgetServerId by rememberSaveable { mutableStateOf<String?>(null) }
    val computerToForget = pairing.computers.firstOrNull { it.serverId == forgetServerId }
    val failure = error ?: connection.message.takeIf { connection.phase == ConnectionPhase.Failed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上一页")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加电脑") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (failure != null) {
                    item(key = "error") {
                        Surface(shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (error == null && connection.host != null) {
                                    val name = pairing.computers.firstOrNull { it.serverId == connection.serverId }?.name
                                        ?: "${connection.host}:${connection.port}"
                                    Text("未能连接“$name”", style = MaterialTheme.typography.titleSmall)
                                }
                                Text(failure, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (pairing.computers.isEmpty()) {
                    item(key = "empty") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(painterResource(R.drawable.ic_computer), contentDescription = null,
                                    modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("还没有保存的电脑", style = MaterialTheme.typography.titleMedium)
                                Text("点击“添加电脑”，扫描电脑端的配对二维码。配对成功后即可在这里连接和管理。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    item(key = "summary") {
                        Text("已保存 ${pairing.computers.size} 台电脑 · 同时向一台电脑同步",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(pairing.computers, key = { it.serverId }) { computer ->
                        ComputerCard(
                            computer = computer,
                            isDefault = computer.serverId == pairing.selectedServerId,
                            connection = connection.takeIf { !it.isTemporary && it.serverId == computer.serverId },
                            onConnect = { onConnect(computer.serverId) },
                            onForget = { forgetServerId = computer.serverId },
                        )
                    }
                }
            }
        }
    }

    if (computerToForget != null) {
        val isConnected = !connection.isTemporary && connection.serverId == computerToForget.serverId &&
            connection.phase == ConnectionPhase.Connected
        val isTarget = !connection.isTemporary && connection.serverId == computerToForget.serverId &&
            (connection.isConnecting || isConnected)
        AlertDialog(
            onDismissRequest = { forgetServerId = null },
            title = { Text("忘记这台电脑？") },
            text = {
                Text(buildString {
                    append("将移除“${computerToForget.name}”在此手机上的配对信息，下次连接需要重新扫码。")
                    if (isTarget) append("当前同步会停止，请手动选择其他电脑。")
                    if (!isConnected) append("电脑端的配对记录可能仍会保留，可在电脑端的设备页移除。")
                })
            },
            confirmButton = {
                TextButton(onClick = { forgetServerId = null; onForget(computerToForget.serverId) }) {
                    Text("忘记电脑", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { forgetServerId = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComputerCard(
    computer: PairedComputer,
    isDefault: Boolean,
    connection: ConnectionState?,
    onConnect: () -> Unit,
    onForget: () -> Unit,
) {
    val addedAt = remember(computer.addedAtMillis) {
        computer.addedAtMillis?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
        } ?: "未记录"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(painterResource(R.drawable.ic_computer), contentDescription = null,
                    modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(computer.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    if (isDefault) Text("默认连接目标", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    connection?.let {
                        Text(it.message.takeUnless { connection.phase == ConnectionPhase.Failed } ?: "连接失败",
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.phase == ConnectionPhase.Failed) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            ComputerField("IP 地址", computer.host)
            ComputerField("端口", computer.port.toString())
            ComputerField("添加时间", addedAt)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onConnect,
                    enabled = connection?.isConnecting != true && connection?.phase != ConnectionPhase.Connected,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(when {
                        connection?.phase == ConnectionPhase.Connected -> "已连接"
                        connection?.isConnecting == true -> "正在连接"
                        connection?.phase == ConnectionPhase.Failed -> "重试连接"
                        else -> "连接此电脑"
                    })
                }
                TextButton(onClick = onForget, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("忘记电脑", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ComputerField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
