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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.youximi.appmapper.data.PairedComputer
import dev.youximi.appmapper.data.PairingParser
import dev.youximi.appmapper.data.PairingRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    pairedComputer: PairedComputer?,
    connectionStatus: String,
    onPair: (PairingRequest) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("8765") }
    var code by remember { mutableStateOf("") }
    var rememberDevice by rememberSaveable { mutableStateOf(true) }
    var inputError by rememberSaveable { mutableStateOf("") }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) PairingParser.parseUri(result.contents)?.let { request ->
            inputError = ""
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            onPair(request)
        } ?: run { inputError = "二维码无效或版本过旧" }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("配对") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ListItem(headlineContent = { Text("运行状态") }, supportingContent = { Text(connectionStatus) })
            pairedComputer?.let { computer ->
                ListItem(headlineContent = { Text("已配对电脑") },
                    supportingContent = { Text("${computer.name} · ${computer.host}:${computer.port}") })
                Button(modifier = Modifier.fillMaxWidth(), onClick = onStart) { Text("连接已配对电脑") }
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onStop) { Text("停止同步") }
            HorizontalDivider()
            Text("首次连接或更换电脑")
            ListItem(headlineContent = { Text("记住设备") },
                supportingContent = { Text(if (rememberDevice) "扫码绑定，断线后可重连" else "手动临时连接，断线后需重新输入验证码") },
                trailingContent = { Switch(checked = rememberDevice, onCheckedChange = {
                    rememberDevice = it
                    inputError = ""
                }) })
            if (rememberDevice) {
                FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = {
                    qrScanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("扫描 AppMapper 配对二维码").setBeepEnabled(false)
                        .setOrientationLocked(false))
                }) { Text("扫码绑定并开始") }
            } else {
                OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = host,
                    onValueChange = { host = it }, label = { Text("电脑 IP") }, singleLine = true)
                OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = port,
                    onValueChange = { port = it.filter(Char::isDigit) }, label = { Text("端口") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) }, label = { Text("6 位验证码") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (inputError.isNotBlank()) Text(inputError)
            if (!rememberDevice) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    val p = port.toIntOrNull()
                    if (host.isBlank() || p == null || p !in 1..65535 || code.length != 6) {
                        inputError = "请填写地址、端口和 6 位验证码"
                    } else {
                        inputError = ""
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        onPair(PairingRequest.Temporary(host.trim(), p, code))
                        code = ""
                    }
                }) { Text("临时连接并开始") }
            }
        }
    }
}
