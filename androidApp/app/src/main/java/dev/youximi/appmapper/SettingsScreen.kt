package dev.youximi.appmapper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    hasUsageAccess: Boolean,
    pollingMs: Long,
    onOpenUsageAccess: () -> Unit,
    onPollingSelected: (Long) -> Unit,
    onOpenLogs: () -> Unit,
) {
    var selectedPolling by rememberSaveable(pollingMs) { mutableStateOf(pollingMs) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("权限", style = MaterialTheme.typography.titleSmall)
            ListItem(
                headlineContent = { Text("权限检测") },
                supportingContent = { Text(if (hasUsageAccess) "使用情况访问权限已授权" else "使用情况访问权限未授权") },
                trailingContent = {
                    TextButton(onClick = onOpenUsageAccess) {
                        Text("打开")
                    }
                },
            )

            HorizontalDivider()

            Text("查询档位", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedPolling == 500L,
                    onClick = {
                        selectedPolling = 500L
                        onPollingSelected(500L)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                ) {
                    Text("500ms")
                }
                SegmentedButton(
                    selected = selectedPolling == 1000L,
                    onClick = {
                        selectedPolling = 1000L
                        onPollingSelected(1000L)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                ) {
                    Text("1000ms")
                }
                SegmentedButton(
                    selected = selectedPolling == 3000L,
                    onClick = {
                        selectedPolling = 3000L
                        onPollingSelected(3000L)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                ) {
                    Text("3000ms")
                }
            }

            HorizontalDivider()

            Text("更多", style = MaterialTheme.typography.titleSmall)
            ListItem(
                headlineContent = { Text("日志") },
                supportingContent = { Text("进入独立日志页面") },
                trailingContent = {
                    TextButton(onClick = onOpenLogs) {
                        Text("进入")
                    }
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("关于") },
                supportingContent = { Text("AppMapper") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("版本信息") },
                supportingContent = { Text("v${BuildConfig.VERSION_NAME}") },
            )
        }
    }
}
