package dev.youximi.appmapper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import dev.youximi.appmapper.data.AppUpdateInfo

@Composable
internal fun UpdateDialog(
    release: AppUpdateInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    downloadError: String?,
) {
    val newer = release.isNewerThan(BuildConfig.VERSION_CODE)
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val notesHeight = with(LocalDensity.current) { windowHeight.toDp() * 0.45f }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (newer) "发现新版本" else "更新说明") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("当前版本 " + BuildConfig.VERSION_NAME + "\n最新版本 " + release.versionName,
                    style = MaterialTheme.typography.bodyMedium)
                SelectionContainer {
                    Column(Modifier.heightIn(max = notesHeight).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("更新说明", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(release.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (downloadError != null) Text(downloadError, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text("前往下载") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (newer) "稍后" else "关闭") } },
    )
}
