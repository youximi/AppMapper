package dev.youximi.appmapper

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.youximi.appmapper.data.UpdateCheckPhase
import dev.youximi.appmapper.data.UpdateCheckState

@Composable
internal fun SettingsScreen(
    hasUsageAccess: Boolean,
    pollingMs: Long,
    dynamicColorEnabled: Boolean,
    themeColor: AppThemeColor,
    onDynamicColorChanged: (Boolean) -> Unit,
    onThemeColorSelected: (AppThemeColor) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onPollingSelected: (Long) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    savedComputerCount: Int,
    onOpenDevices: () -> Unit,
) {
    var showThemePicker by rememberSaveable { mutableStateOf(false) }
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamicColor = dynamicColorAvailable && dynamicColorEnabled
    val pollingOptions = listOf(
        PollingOption("快速", 500L, "每 0.5 秒检测一次，响应更及时"),
        PollingOption("标准", 1000L, "每 1 秒检测一次，兼顾响应与耗电"),
        PollingOption("省电", 3000L, "每 3 秒检测一次，减少后台查询"),
    )
    val rowColors = ListItemDefaults.colors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
    )
    val groupColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

    ScreenContent {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("应用权限")
            Card(shape = MaterialTheme.shapes.large, colors = groupColors) {
                ListItem(
                    onClick = onOpenUsageAccess,
                    modifier = Modifier.semantics { role = Role.Button },
                    colors = rowColors,
                    content = { Text("使用情况访问") },
                    supportingContent = { Text(if (hasUsageAccess) "已授权，可以读取前台应用" else "尚未授权，点击前往设置") },
                    leadingContent = {
                        Icon(painterResource(if (hasUsageAccess) R.drawable.ic_check_circle else R.drawable.ic_warning),
                            contentDescription = null,
                            tint = if (hasUsageAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("外观")
            Card(shape = MaterialTheme.shapes.large, colors = groupColors) {
                ListItem(
                    modifier = Modifier.toggleable(
                        value = useDynamicColor,
                        enabled = dynamicColorAvailable,
                        role = Role.Switch,
                        onValueChange = onDynamicColorChanged,
                    ),
                    enabled = dynamicColorAvailable,
                    colors = rowColors,
                    content = { Text("莫奈取色") },
                    supportingContent = {
                        Text(when {
                            !dynamicColorAvailable -> "需要 Android 12 或更高版本"
                            useDynamicColor -> "使用系统壁纸与配色生成的主题色"
                            else -> "使用下方选择的自定义主题色"
                        })
                    },
                    trailingContent = {
                        Switch(checked = useDynamicColor, onCheckedChange = null, enabled = dynamicColorAvailable)
                    },
                )
                ListItem(
                    onClick = { showThemePicker = true },
                    modifier = Modifier.semantics { role = Role.Button },
                    enabled = !useDynamicColor,
                    colors = rowColors,
                    content = { Text("自定义主题色") },
                    supportingContent = {
                        Text(if (useDynamicColor) "关闭莫奈取色后可选择（已保存：${themeColor.label}）"
                            else "当前：${themeColor.label}")
                    },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
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

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("设备")
            Card(shape = MaterialTheme.shapes.large, colors = groupColors) {
                ListItem(
                    onClick = onOpenDevices,
                    modifier = Modifier.semantics { role = Role.Button },
                    colors = rowColors,
                    content = { Text("设备管理") },
                    supportingContent = { Text(if (savedComputerCount == 0) "添加电脑并管理连接目标" else "已保存 $savedComputerCount 台电脑") },
                    leadingContent = { Icon(painterResource(R.drawable.ic_computer), contentDescription = null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("更多")
            Card(shape = MaterialTheme.shapes.large, colors = groupColors) {
                ListItem(
                    onClick = onOpenLogs,
                    modifier = Modifier.semantics { role = Role.Button },
                    colors = rowColors,
                    content = { Text("连接日志") },
                    supportingContent = { Text("查看连接记录与排查信息") },
                    leadingContent = { Icon(painterResource(R.drawable.ic_list), contentDescription = null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                )
                ListItem(
                    onClick = onOpenAbout,
                    modifier = Modifier.semantics { role = Role.Button },
                    colors = rowColors,
                    content = { Text("关于应用") },
                    supportingContent = { Text("版本、更新与开源许可") },
                    leadingContent = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                )
            }
        }
    }

    if (showThemePicker && !useDynamicColor) {
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("自定义主题色") },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    AppThemeColor.entries.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = themeColor == option, role = Role.RadioButton, onClick = {
                                    onThemeColorSelected(option)
                                    showThemePicker = false
                                })
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = themeColor == option, onClick = null)
                            Surface(modifier = Modifier.size(24.dp), shape = CircleShape, color = option.light.primary) {}
                            Text(option.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemePicker = false }) { Text("取消") } },
        )
    }

}

@Composable
internal fun AboutScreen(
    updateState: UpdateCheckState,
    includePrereleases: Boolean,
    onUpdateChannelSelected: (Boolean) -> Unit,
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenUpdateNotes: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    var showUpdateChannelPicker by rememberSaveable { mutableStateOf(false) }
    val canCheckUpdates = updateState.phase != UpdateCheckPhase.Checking
    val repositoryUrl = "https://github.com/youximi/AppMapper"
    val rowColors = ListItemDefaults.colors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
    )
    val groupColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val hasUpdateNotes = updateState.release != null && updateState.phase != UpdateCheckPhase.Checking

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于应用") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回设置")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            ScreenContent {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painterResource(R.drawable.ic_appmapper),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                    Text("AppMapper", style = MaterialTheme.typography.headlineSmall)
                    Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("版本与更新")
                    Card(shape = MaterialTheme.shapes.large, colors = groupColors) {
                        ListItem(
                            onClick = onCheckUpdates,
                            modifier = Modifier.semantics { role = Role.Button },
                            enabled = canCheckUpdates,
                            colors = rowColors,
                            content = { Text("检查更新") },
                            supportingContent = { Text(updateState.message,
                                color = if (updateState.phase == UpdateCheckPhase.Failed)
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                        )
                        ListItem(
                            onClick = { showUpdateChannelPicker = true },
                            modifier = Modifier.semantics { role = Role.Button },
                            enabled = canCheckUpdates,
                            colors = rowColors,
                            content = { Text("更新通道") },
                            supportingContent = {
                                Text(if (includePrereleases) "测试版 · 包含正式版和测试版，测试版极不稳定，仅供测试"
                                    else "正式版 · 仅接收正式版更新")
                            },
                            leadingContent = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                        )
                        ListItem(
                            onClick = onOpenUpdateNotes,
                            modifier = Modifier.semantics { role = Role.Button },
                            enabled = hasUpdateNotes,
                            colors = rowColors,
                            content = { Text("更新说明") },
                            supportingContent = { Text(updateState.release?.let { "最新版本 " + it.versionName }
                                ?: "检查成功后可查看 Git 提交记录") },
                            leadingContent = { Icon(painterResource(R.drawable.ic_list), contentDescription = null) },
                            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("开源信息")
                    Card(shape = MaterialTheme.shapes.large, colors = groupColors) {
                        ListItem(
                            modifier = Modifier.semantics { role = Role.Button },
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, repositoryUrl.toUri()))
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, "无法打开浏览器，请安装或启用浏览器后重试",
                                        Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = rowColors,
                            content = { Text("开源仓库") },
                            supportingContent = { Text(repositoryUrl) },
                            leadingContent = { Icon(painterResource(R.drawable.ic_list), contentDescription = null) },
                            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                        )
                        ListItem(
                            onClick = onOpenLicenses,
                            modifier = Modifier.semantics { role = Role.Button },
                            colors = rowColors,
                            content = { Text("开源许可") },
                            supportingContent = { Text("查看第三方库与开源协议") },
                            leadingContent = { Icon(painterResource(R.drawable.ic_list), contentDescription = null) },
                            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
    if (showUpdateChannelPicker) {
        AlertDialog(
            onDismissRequest = { showUpdateChannelPicker = false },
            title = { Text("更新通道") },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    listOf(false, true).forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = includePrereleases == option, enabled = canCheckUpdates,
                                    role = Role.RadioButton, onClick = {
                                        onUpdateChannelSelected(option)
                                        showUpdateChannelPicker = false
                                    })
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = includePrereleases == option, onClick = null, enabled = canCheckUpdates)
                            Column {
                                Text(if (option) "测试版" else "正式版")
                                Text(if (option) "包含正式版和测试版更新；测试版极不稳定，仅供测试" else "仅接收正式版更新",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showUpdateChannelPicker = false }) { Text("取消") } },
        )
    }
}

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
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = if (selectedDocument == null) "返回关于应用" else "返回开源许可",
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
                                    onClick = { selectedDocument = library.id },
                                    modifier = Modifier.semantics { role = Role.Button }
                                        .padding(vertical = 8.dp),
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent),
                                    content = { Text(library.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text("${library.version}\n${library.license}") },
                                    leadingContent = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
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
