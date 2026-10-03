package dev.youximi.appmapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.youximi.appmapper.data.AppLogger
import dev.youximi.appmapper.data.SettingsStore
import dev.youximi.appmapper.data.UsageAppReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLogger.write(this, "MainActivity created.")

        setContent {
            AppRoot()
        }
    }
}

@Composable
internal fun AppRoot() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val settings = remember(context) { SettingsStore(context) }
    var dynamicColorEnabled by rememberSaveable { mutableStateOf(settings.getDynamicColorEnabled()) }
    var themeColor by rememberSaveable {
        val savedThemeColor = settings.getThemeColor()
        mutableStateOf(AppThemeColor.entries.firstOrNull { it.name == savedThemeColor } ?: AppThemeColor.Default)
    }
    val coordinator = remember(activity) {
        AppCoordinator(
            activity = activity,
            settings = settings,
            usageReader = UsageAppReader(context),
        )
    }
    AppTheme(dynamicColorEnabled = dynamicColorEnabled, themeColor = themeColor) {
        AppScaffold(
            coordinator = coordinator,
            dynamicColorEnabled = dynamicColorEnabled,
            themeColor = themeColor,
            onDynamicColorChanged = {
                dynamicColorEnabled = it
                settings.saveDynamicColorEnabled(it)
            },
            onThemeColorSelected = {
                themeColor = it
                settings.saveThemeColor(it.name)
            },
        )
    }
}
