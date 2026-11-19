package app.jisr.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.remember
import app.jisr.vpn.data.Prefs
import app.jisr.vpn.ui.BottomBar
import app.jisr.vpn.ui.C
import app.jisr.vpn.ui.GlassBackground
import app.jisr.vpn.ui.HomeScreen
import app.jisr.vpn.ui.ServersScreen
import app.jisr.vpn.ui.SettingsScreen
import app.jisr.vpn.vpn.VpnController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        VpnController.register(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = C.Teal, background = C.Bg, surface = C.Bg)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            message = VpnController.start(ctx)
        } else {
            message = "لازم توافق على إذن الـVPN عشان يشتغل الاتصال"
        }
    }

    fun connectFlow() {
        message = null
        val prep: Intent? = VpnService.prepare(ctx)
        if (prep != null) vpnPermission.launch(prep) else message = VpnController.start(ctx)
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        if (Prefs.autoConnect && VpnController.status == "disconnected") connectFlow()
    }

    GlassBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeScreen(
                        message = message,
                        onToggle = {
                            if (VpnController.status == "disconnected" || VpnController.status == "error") connectFlow()
                            else VpnController.stop(ctx)
                        },
                        onOpenServers = { tab = 1 }
                    )
                    1 -> ServersScreen(onPicked = { tab = 0 })
                    else -> SettingsScreen()
                }
            }
            BottomBar(tab) { tab = it }
        }
    }
}
