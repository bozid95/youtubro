package pro.iyou

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import pro.iyou.playback.PlayerConnection
import pro.iyou.ui.IyouRoot
import pro.iyou.ui.theme.IyouTheme

class MainActivity : ComponentActivity() {

    private lateinit var player: PlayerConnection
    private val links = MutableStateFlow<DeepLink?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the notification is optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        player = PlayerConnection(this, lifecycleScope, app.container.repository, app.container.settings)
        if (savedInstanceState == null) links.value = DeepLink.from(intent)

        // Android 13+ asks before showing the media notification
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val link by links.collectAsState()
            IyouTheme {
                IyouRoot(player, link, onLinkHandled = { links.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLink.from(intent)?.let { links.value = it }
    }

    override fun onStart() {
        super.onStart()
        player.setForeground(true)
    }

    override fun onStop() {
        player.setForeground(false)
        super.onStop()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
