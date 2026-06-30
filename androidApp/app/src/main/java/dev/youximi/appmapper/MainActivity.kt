package dev.youximi.appmapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.youximi.appmapper.data.AppLogger
import dev.youximi.appmapper.data.SettingsStore
import dev.youximi.appmapper.data.UsageAppReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.write(this, "MainActivity created.")

        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }
}

@Composable
internal fun AppRoot() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val coordinator = androidx.compose.runtime.remember(activity) {
        AppCoordinator(
            activity = activity,
            settings = SettingsStore(context),
            usageReader = UsageAppReader(context),
        )
    }
    AppScaffold(coordinator = coordinator)
}
