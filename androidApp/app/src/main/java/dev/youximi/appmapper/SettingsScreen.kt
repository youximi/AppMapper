package dev.youximi.appmapper

import dev.youximi.appmapper.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings

@Composable
internal fun SettingsScreen(
    hasUsageAccess: Boolean,
    pollingMs: Long,
    onOpenUsageAccess: () -> Unit,
    onPollingSelected: (Long) -> Unit,
    onOpenLogs: () -> Unit,
) {
    var selectedPolling by rememberSaveable(pollingMs) { mutableStateOf(pollingMs) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置")

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("权限检测") },
                supportingContent = { Text(if (hasUsageAccess) "使用情况访问权限已授权" else "使用情况访问权限未授权") },
                leadingContent = { Icon(Icons.Filled.Security, contentDescription = null) },
                trailingContent = { TextButton(onClick = onOpenUsageAccess) { Text("打开") } },
            )
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("查询档位")
                PollingChoice("500ms", 500, selectedPolling) {
                    selectedPolling = it
                    onPollingSelected(it)
                }
                PollingChoice("1000ms", 1000, selectedPolling) {
                    selectedPolling = it
                    onPollingSelected(it)
                }
                PollingChoice("3000ms", 3000, selectedPolling) {
                    selectedPolling = it
                    onPollingSelected(it)
                }
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("日志") },
                supportingContent = { Text("进入独立日志页面") },
                leadingContent = { Icon(Icons.Filled.Description, contentDescription = null) },
                trailingContent = { TextButton(onClick = onOpenLogs) { Text("进入") } },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("关于") },
                supportingContent = { Text("AppMapper") },
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("版本信息") },
                supportingContent = { Text("v${BuildConfig.VERSION_NAME}") },
                leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
            )
        }

        Text("后续所有设置项将放在这里")
    }
}

@Composable
private fun PollingChoice(label: String, value: Long, selected: Long, onSelect: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(label)
    }
}
