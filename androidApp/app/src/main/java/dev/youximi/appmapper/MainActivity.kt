package dev.youximi.appmapper

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.youximi.appmapper.data.AppLogger
import dev.youximi.appmapper.data.PairingParser
import dev.youximi.appmapper.data.PairingTarget
import dev.youximi.appmapper.data.SettingsStore
import dev.youximi.appmapper.data.UsageAppReader
import dev.youximi.appmapper.service.ForegroundSyncService
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsStore(this)
        val usageReader = UsageAppReader(this)
        AppLogger.write(this, "MainActivity created.")

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        initialTarget = settings.getTarget(),
                        initialPollingMs = settings.getPollingMs(),
                        hasUsageAccess = usageReader.hasUsageAccess(),
                        onSaveTarget = { target ->
                            AppLogger.write(this, "Pairing target saved: ${target.host}:${target.port}, codeLength=${target.code.length}.")
                            settings.saveTarget(target)
                        },
                        onSavePolling = { value ->
                            AppLogger.write(this, "Polling interval saved: ${value}ms.")
                            settings.savePollingMs(value)
                        },
                        onOpenUsageAccess = {
                            AppLogger.write(this, "Opening usage access settings.")
                            startActivity(usageReader.usageAccessIntent())
                        },
                        onStart = {
                            AppLogger.write(this, "Start requested from UI.")
                            ForegroundSyncService.start(this)
                        },
                        onStop = {
                            AppLogger.write(this, "Stop requested from UI.")
                            ForegroundSyncService.stop(this)
                        },
                        onLoadLogs = { AppLogger.read(this) },
                        onExportLogs = { exportLogs() },
                        onClearLogs = { AppLogger.clear(this) },
                    )
                }
            }
        }
    }

    private fun exportLogs() {
        AppLogger.write(this, "Logs export requested.")
        val logs = AppLogger.read(this).ifBlank { "AppMapper log is empty." }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AppMapper logs")
            putExtra(Intent.EXTRA_TEXT, logs)
        }
        startActivity(Intent.createChooser(sendIntent, "导出 AppMapper 日志"))
    }
}

@Composable
private fun MainScreen(
    initialTarget: PairingTarget,
    initialPollingMs: Long,
    hasUsageAccess: Boolean,
    onSaveTarget: (PairingTarget) -> Unit,
    onSavePolling: (Long) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLoadLogs: () -> String,
    onExportLogs: () -> Unit,
    onClearLogs: () -> Unit,
) {
    var host by remember { mutableStateOf(initialTarget.host) }
    var port by remember { mutableStateOf(initialTarget.port.toString()) }
    var code by remember { mutableStateOf(initialTarget.code) }
    var uri by remember { mutableStateOf("") }
    var pollingMs by remember { mutableStateOf(initialPollingMs) }
    var logs by remember { mutableStateOf(onLoadLogs()) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val content = result.contents ?: return@rememberLauncherForActivityResult
        uri = content
        PairingParser.parseUri(content)?.let { parsed ->
            host = parsed.host
            port = parsed.port.toString()
            code = parsed.code
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("AppMapper", style = MaterialTheme.typography.headlineMedium)
        Text(if (hasUsageAccess) "使用情况访问权限：已授权" else "使用情况访问权限：未授权")
        Button(onClick = onOpenUsageAccess) {
            Text("打开使用情况访问权限")
        }

        Text("配对")
        Button(onClick = {
            qrScanner.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("扫描 AppMapper 配对二维码")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
        }) {
            Text("扫描二维码")
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uri,
            onValueChange = {
                uri = it
                PairingParser.parseUri(it)?.let { parsed ->
                    host = parsed.host
                    port = parsed.port.toString()
                    code = parsed.code
                }
            },
            label = { Text("二维码内容") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = host,
            onValueChange = { host = it },
            label = { Text("IP") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("端口") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        Text("查询档位")
        PollingOption("快速 500ms", 500, pollingMs) { pollingMs = it }
        PollingOption("标准 1000ms", 1000, pollingMs) { pollingMs = it }
        PollingOption("省电 3000ms", 3000, pollingMs) { pollingMs = it }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val target = PairingTarget(host.trim(), port.toIntOrNull() ?: 8765, code.trim())
                onSaveTarget(target)
                onSavePolling(pollingMs)
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onStart()
            }) {
                Text("保存并开始")
            }
            Button(onClick = onStop) {
                Text("停止")
            }
        }

        Text("日志")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { logs = onLoadLogs() }) {
                Text("刷新日志")
            }
            Button(onClick = onExportLogs) {
                Text("导出日志")
            }
            Button(onClick = {
                onClearLogs()
                logs = onLoadLogs()
            }) {
                Text("清空日志")
            }
        }

        val preview = logs.takeLast(8_000).ifBlank { "暂无日志" }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            SelectionContainer {
                Text(
                    text = preview,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PollingOption(label: String, value: Long, selected: Long, onSelect: (Long) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}
