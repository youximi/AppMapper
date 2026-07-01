package dev.youximi.appmapper

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.youximi.appmapper.data.PairingParser
import dev.youximi.appmapper.data.PairingTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    initialTarget: PairingTarget,
    onSaveTarget: (PairingTarget) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var host by rememberSaveable(initialTarget.host) { mutableStateOf(initialTarget.host) }
    var port by rememberSaveable(initialTarget.port) { mutableStateOf(initialTarget.port.toString()) }
    var code by rememberSaveable(initialTarget.code) { mutableStateOf(initialTarget.code) }
    var connectionStatus by rememberSaveable { mutableStateOf("未连接") }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val content = result.contents ?: return@rememberLauncherForActivityResult
        PairingParser.parseUri(content)?.let { parsed ->
            host = parsed.host
            port = parsed.port.toString()
            code = parsed.code
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("配对") }) },
        bottomBar = {},
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ListItem(
                headlineContent = { Text("运行状态") },
                supportingContent = { Text(connectionStatus) },
            )

            HorizontalDivider()

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = host,
                onValueChange = { host = it },
                label = { Text("IP") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("端口") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val target = PairingTarget(host.trim(), port.toIntOrNull() ?: 8765, code.trim())
                    onSaveTarget(target)
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    connectionStatus = "已保存"
                    onStart()
                },
            ) {
                Text("开始")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    connectionStatus = "已停止"
                    onStop()
                },
            ) {
                Text("停止")
            }

            HorizontalDivider()

            Text("二维码", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    qrScanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("扫描 AppMapper 配对二维码")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false),
                    )
                },
            ) {
                Text("扫描二维码")
            }
        }
    }
}
