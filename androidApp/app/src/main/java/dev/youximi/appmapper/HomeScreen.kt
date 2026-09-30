package dev.youximi.appmapper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import dev.youximi.appmapper.data.PairedComputer
import dev.youximi.appmapper.data.PairingRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    pairedComputer: PairedComputer?,
    connectionStatus: String,
    isSyncRunning: Boolean,
    scanError: String,
    onScan: () -> Unit,
    onPair: (PairingRequest) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("8765") }
    var code by remember { mutableStateOf("") }
    var useQrCode by rememberSaveable { mutableStateOf(true) }
    var showPairing by rememberSaveable(pairedComputer != null) { mutableStateOf(pairedComputer == null) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val hostInvalid = host.isBlank()
    val portInvalid = port.toIntOrNull()?.let { it !in 1..65535 } ?: true
    val codeInvalid = code.length != 6
    fun connectTemporarily() {
        submitted = true
        if (!hostInvalid && !portInvalid && !codeInvalid) {
            onPair(PairingRequest.Temporary(host.trim(), port.toInt(), code))
            code = ""
            submitted = false
        }
    }

    ScreenContent {
        ConnectionCard(
            pairedComputer = pairedComputer,
            connectionStatus = connectionStatus,
            isSyncRunning = isSyncRunning,
            onStart = onStart,
            onStop = onStop,
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pairedComputer != null) {
                TextButton(onClick = { showPairing = !showPairing }) {
                    Text(if (showPairing) "收起配对选项" else "连接其他电脑")
                }
            } else {
                SectionTitle("开始连接")
            }

            AnimatedVisibility(visible = showPairing) {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        modifier = Modifier.animateContentSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("手机和电脑需连接同一局域网", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("扫码绑定", "临时连接").forEachIndexed { index, label ->
                                SegmentedButton(
                                    selected = useQrCode == (index == 0),
                                    onClick = {
                                        useQrCode = index == 0
                                        submitted = false
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                                ) { Text(label) }
                            }
                        }

                        if (useQrCode) {
                            Text("绑定后记住这台电脑，断线时自动重连。",
                                style = MaterialTheme.typography.bodyLarge)
                            Button(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                onClick = onScan,
                            ) {
                                Icon(painterResource(R.drawable.ic_qr_code), contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("扫码绑定并开始")
                            }
                        } else {
                            Text("仅连接本次，不会替换已保存的电脑。断线后需重新输入验证码。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = host,
                                onValueChange = { host = it },
                                label = { Text("电脑 IP") },
                                placeholder = { Text("例如 192.168.1.10") },
                                singleLine = true,
                                isError = submitted && hostInvalid,
                                supportingText = if (submitted && hostInvalid) ({ Text("请输入电脑 IP") }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = port,
                                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                                label = { Text("端口") },
                                singleLine = true,
                                isError = submitted && portInvalid,
                                supportingText = if (submitted && portInvalid) ({ Text("端口范围为 1–65535") }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            )
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = code,
                                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                                label = { Text("6 位验证码") },
                                singleLine = true,
                                isError = submitted && codeInvalid,
                                supportingText = if (submitted && codeInvalid) ({ Text("请输入电脑端显示的 6 位验证码") }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { connectTemporarily() }),
                            )
                            Button(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                onClick = ::connectTemporarily,
                            ) { Text("临时连接并开始") }
                        }

                        if (useQrCode && scanError.isNotBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ) {
                                Text(scanError,
                                    modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    pairedComputer: PairedComputer?,
    connectionStatus: String,
    isSyncRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Icon(painterResource(R.drawable.ic_computer), contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(24.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text("连接状态", style = MaterialTheme.typography.labelLarge)
                    Text(connectionStatus,
                        modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.headlineSmall)
                }
            }
            if (pairedComputer != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("已保存的电脑", style = MaterialTheme.typography.labelMedium)
                    Text(pairedComputer.name, style = MaterialTheme.typography.titleMedium)
                    Text("${pairedComputer.host}:${pairedComputer.port}", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("将手机上的前台应用同步到电脑，让使用时间自动记录。",
                    style = MaterialTheme.typography.bodyLarge)
            }
            if (isSyncRunning) {
                FilledTonalButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = onStop) {
                    Text("停止同步")
                }
            } else if (pairedComputer != null) {
                Button(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = onStart) {
                    Text("连接已配对电脑")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrScannerScreen(onBack: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun cameraGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(cameraGranted()) }
    var requestedPermission by rememberSaveable { mutableStateOf(false) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !requestedPermission) {
            requestedPermission = true
            permissionRequest.launch(Manifest.permission.CAMERA)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = cameraGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("扫码绑定") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回首页")
                }
            },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            ScreenContent {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("扫描电脑上的二维码", style = MaterialTheme.typography.headlineSmall)
                    Text("打开电脑端 AppMapper 的配对页面，将二维码放入取景框。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hasPermission) {
                    CameraScanner(onResult = onResult)
                } else {
                    Surface(shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("需要相机权限", style = MaterialTheme.typography.titleLarge)
                            Text("相机仅用于识别电脑上的配对二维码。你也可以返回首页选择临时连接。",
                                style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { permissionRequest.launch(Manifest.permission.CAMERA) }) {
                                Text("允许使用相机")
                            }
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)))
                            }) { Text("打开应用权限设置") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraScanner(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraError by remember { mutableStateOf(false) }
    val barcodeView = remember {
        BarcodeView(context).apply {
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            addStateListener(object : CameraPreview.StateListener {
                override fun previewSized() = Unit
                override fun previewStarted() = Unit
                override fun previewStopped() = Unit
                override fun cameraClosed() = Unit
                override fun cameraError(error: Exception) { cameraError = true }
            })
            decodeSingle(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) { onResult(result.text) }
            })
        }
    }

    DisposableEffect(barcodeView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> barcodeView.resume()
                Lifecycle.Event.ON_PAUSE -> barcodeView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            barcodeView.pause()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(factory = { barcodeView }, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize(0.8f).border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large))
        }
        if (cameraError) {
            Text("相机暂时无法启动，请返回后重试，或使用临时连接。",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        } else {
            Text("识别成功后会自动返回并开始连接", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
