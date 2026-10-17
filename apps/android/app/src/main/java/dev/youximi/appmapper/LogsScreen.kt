package dev.youximi.appmapper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun LogsScreen(coordinator: AppCoordinator, onBack: () -> Unit) {
    var logs by remember { mutableStateOf(coordinator.readLogs()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回设置")
                    }
                },
                actions = {
                    IconButton(onClick = { logs = coordinator.readLogs() }) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "刷新日志")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "更多日志操作")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("导出日志") },
                                onClick = { menuExpanded = false; coordinator.exportLogs() },
                            )
                            DropdownMenuItem(
                                text = { Text("清空日志", color = MaterialTheme.colorScheme.error) },
                                enabled = logs.isNotBlank(),
                                onClick = { menuExpanded = false; confirmClear = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (logs.isBlank()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(R.drawable.ic_list), contentDescription = null,
                        modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("暂无日志", style = MaterialTheme.typography.titleLarge)
                    Text("连接活动会记录在这里", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        SelectionContainer {
                            Text(
                                text = logs,
                                modifier = Modifier.padding(16.dp),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空连接日志？") },
            text = { Text("现有日志将被删除。需要保留时，可以先导出。") },
            confirmButton = {
                TextButton(onClick = {
                    coordinator.clearLogs()
                    logs = coordinator.readLogs()
                    confirmClear = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}
