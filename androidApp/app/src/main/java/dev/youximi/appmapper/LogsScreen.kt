package dev.youximi.appmapper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh

@Composable
internal fun LogsScreen(
    coordinator: AppCoordinator,
    onBack: () -> Unit,
) {
    var logs by remember { mutableStateOf(coordinator.readLogs()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ListItem(
            headlineContent = { Text("日志") },
            leadingContent = { Icon(Icons.Filled.ArrowBack, contentDescription = null) },
            trailingContent = { TextButton(onClick = onBack) { Text("返回") } },
        )

        RowActions(
            onRefresh = { logs = coordinator.readLogs() },
            onExport = coordinator::exportLogs,
            onClear = {
                coordinator.clearLogs()
                logs = coordinator.readLogs()
            },
        )

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(
                    text = logs.ifBlank { "暂无日志" },
                    modifier = Modifier.padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun RowActions(onRefresh: () -> Unit, onExport: () -> Unit, onClear: () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text("刷新", modifier = Modifier.padding(start = 8.dp))
        }
        FilledTonalButton(onClick = onExport) {
            Text("导出")
        }
        ElevatedButton(onClick = onClear) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text("清空", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
