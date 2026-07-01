package dev.youximi.appmapper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    var pollingMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val pollingOptions = listOf(
        PollingOption(name = "快速", value = 500L),
        PollingOption(name = "标准", value = 1000L),
        PollingOption(name = "省电", value = 3000L),
    )
    val selectedPollingOption = pollingOptions.firstOrNull { it.value == selectedPolling }

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

            ListItem(
                headlineContent = { Text("查询档位") },
                trailingContent = {
                    ExposedDropdownMenuBox(
                        expanded = pollingMenuExpanded,
                        onExpandedChange = { pollingMenuExpanded = it },
                    ) {
                        Row(
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                selectedPollingOption?.selectedLabel ?: "${selectedPolling}ms",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                            )
                        }

                        ExposedDropdownMenu(
                            expanded = pollingMenuExpanded,
                            onDismissRequest = { pollingMenuExpanded = false },
                            modifier = Modifier.widthIn(min = 140.dp, max = 180.dp),
                            matchTextFieldWidth = false,
                        ) {
                            pollingOptions.forEach { option ->
                                val selected = selectedPolling == option.value
                                DropdownMenuItem(
                                    text = { Text(option.menuLabel) },
                                    trailingIcon = {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedPolling = option.value
                                        onPollingSelected(option.value)
                                        pollingMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )

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

private data class PollingOption(
    val name: String,
    val value: Long,
) {
    val menuLabel: String = "$name（${value}ms）"
    val selectedLabel: String = "$name ${value}ms"
}
