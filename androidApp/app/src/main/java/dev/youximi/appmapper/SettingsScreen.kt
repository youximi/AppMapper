package dev.youximi.appmapper

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.youximi.appmapper.data.PairedComputer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    hasUsageAccess: Boolean,
    pollingMs: Long,
    onOpenUsageAccess: () -> Unit,
    onPollingSelected: (Long) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenLicenses: () -> Unit,
    pairedComputer: PairedComputer?,
    onForgetComputer: () -> Unit,
) {
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    val pollingOptions = listOf(
        PollingOption("快速", 500L, "每 0.5 秒检测一次，响应更及时"),
        PollingOption("标准", 1000L, "每 1 秒检测一次，兼顾响应与耗电"),
        PollingOption("省电", 3000L, "每 3 秒检测一次，减少后台查询"),
    )
    val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    val groupColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

    ScreenContent {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("应用权限")
            Card(shape = MaterialTheme.shapes.large, colors = groupColors) {
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenUsageAccess),
                    colors = rowColors,
                    headlineContent = { Text("使用情况访问") },
                    supportingContent = { Text(if (hasUsageAccess) "已授权，可以读取前台应用" else "尚未授权，点击前往设置") },
                    leadingContent = {
                        Icon(if (hasUsageAccess) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = if (hasUsageAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("同步频率")
            Card(shape = MaterialTheme.shapes.large, colors = groupColors) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("前台应用检测", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        pollingOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = pollingMs == option.value,
                                onClick = { onPollingSelected(option.value) },
                                shape = SegmentedButtonDefaults.itemShape(index, pollingOptions.size),
                            ) { Text(option.name) }
                        }
                    }
                    Text(
                        pollingOptions.firstOrNull { it.value == pollingMs }?.description ?: "每 ${pollingMs}ms 检测一次",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (pairedComputer != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("已保存的电脑")
                Card(shape = MaterialTheme.shapes.large, colors = groupColors) {
                    ListItem(
                        colors = rowColors,
                        headlineContent = { Text(pairedComputer.name) },
                        supportingContent = { Text("${pairedComputer.host}:${pairedComputer.port}") },
                        leadingContent = { Icon(painterResource(R.drawable.ic_computer), contentDescription = null) },
                    )
                    TextButton(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp), onClick = { confirmForget = true }) {
                        Text("忘记这台电脑", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("更多")
            Card(shape = MaterialTheme.shapes.large, colors = groupColors) {
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenLogs),
                    colors = rowColors,
                    headlineContent = { Text("连接日志") },
                    supportingContent = { Text("查看连接记录与排查信息") },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                )
                ListItem(
                    colors = rowColors,
                    headlineContent = { Text("AppMapper") },
                    supportingContent = { Text("版本 ${BuildConfig.VERSION_NAME}") },
                    leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                )
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenLicenses),
                    colors = rowColors,
                    headlineContent = { Text("开源许可") },
                    supportingContent = { Text("查看第三方库与开源协议") },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                )
            }
        }
    }

    if (confirmForget && pairedComputer != null) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("忘记这台电脑？") },
            text = { Text("将移除与“${pairedComputer.name}”的配对。下次连接需要重新扫码。") },
            confirmButton = {
                TextButton(onClick = { confirmForget = false; onForgetComputer() }) {
                    Text("忘记电脑", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val notices = remember(context) {
        context.assets.open("THIRD_PARTY_LICENSES.md").bufferedReader(Charsets.UTF_8)
            .use { it.readText() }.replace("\r\n", "\n")
    }
    val projectLicense = remember(context) {
        context.assets.open("LICENSE").bufferedReader(Charsets.UTF_8)
            .use { it.readText() }.replace("\r\n", "\n")
    }
    val libraries = remember(notices) {
        // Reuse the Android table in the bundled notices as the single library list.
        notices.substringAfter("## Android 发行版依赖\n").substringBefore("\n许可来源：")
            .lineSequence().filter { it.startsWith("| `") }
            .flatMap { row ->
                val columns = row.split('|').map { it.trim() }
                columns[2].split(',').map { artifact ->
                    LicensedLibrary(
                        group = columns[1].trim('`'),
                        artifact = artifact.trim().trim('`'),
                        version = columns[3].trim('`'),
                        license = columns[4],
                    )
                }
            }
            .sortedWith(compareBy({ it.name }, { it.id })).toList()
    }
    val licenseTexts = remember(notices) {
        mapOf("Apache-2.0" to notices.substringAfter("### Apache License, Version 2.0")
            .substringAfter("```text").substringBefore("```").trim())
    }
    var selectedDocument by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedLibrary = libraries.firstOrNull { it.id == selectedDocument }
    val documentTitle = when (selectedDocument) {
        "LICENSE" -> "项目许可"
        "THIRD_PARTY_LICENSES.md" -> "完整第三方声明"
        else -> selectedLibrary?.name ?: "开源许可"
    }
    val documentText = when (selectedDocument) {
        "LICENSE" -> projectLicense
        "THIRD_PARTY_LICENSES.md" -> notices
        else -> selectedLibrary?.let {
            "${it.id}\n\n版本 ${it.version}\n\n${it.license}\n\n${licenseTexts.getValue(it.license)}"
        }
    }
    val libraryListState = rememberLazyListState()
    val goBack = { if (selectedDocument != null) selectedDocument = null else onBack() }
    BackHandler(enabled = selectedDocument != null) { selectedDocument = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(documentTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selectedDocument == null) "返回设置" else "返回开源许可",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (documentText == null) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    state = libraryListState,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("引用库")
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("AppMapper Android 发行版使用以下开源库。点击条目可查看版本信息和许可全文。")
                                    Text("共包含 ${libraries.size} 个库", style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                    Row {
                                        TextButton(onClick = { selectedDocument = "LICENSE" }) { Text("项目许可") }
                                        TextButton(onClick = { selectedDocument = "THIRD_PARTY_LICENSES.md" }) { Text("完整声明") }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column(Modifier.padding(top = 24.dp, bottom = 12.dp)) { SectionTitle("库列表") }
                    }
                    itemsIndexed(libraries, key = { _, library -> library.id }) { index, library ->
                        val shape = when {
                            libraries.size == 1 -> MaterialTheme.shapes.extraLarge
                            index == 0 -> MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
                            index == libraries.lastIndex -> MaterialTheme.shapes.extraLarge.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))
                            else -> RectangleShape
                        }
                        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column {
                                ListItem(
                                    modifier = Modifier.clickable(role = Role.Button) { selectedDocument = library.id }
                                        .padding(vertical = 8.dp),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    headlineContent = { Text(library.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text("${library.version}\n${library.license}") },
                                    leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                )
                                if (index != libraries.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            } else {
                key(selectedDocument) {
                    val paragraphs = remember(documentText) { documentText.split("\n\n") }
                    LazyColumn(
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(paragraphs) { paragraph ->
                            SelectionContainer {
                                Text(paragraph, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class LicensedLibrary(val group: String, val artifact: String, val version: String, val license: String) {
    val id = "$group:$artifact"
    val name = artifact.removeSuffix("-android").split('-').joinToString(" ") {
        if (it in setOf("ui", "ktx", "jvm")) it.uppercase() else it.replaceFirstChar(Char::uppercase)
    }
}

private data class PollingOption(val name: String, val value: Long, val description: String)
