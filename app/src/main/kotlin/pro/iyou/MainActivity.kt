package pro.iyou

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import pro.iyou.playback.PlayerConnection
import pro.iyou.ui.IyouRoot
import pro.iyou.ui.theme.IyouTheme

class MainActivity : ComponentActivity() {

    private lateinit var player: PlayerConnection

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the notification is optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        player = PlayerConnection(this, lifecycleScope, app.container.repository)

        // Android 13+ asks before showing the media notification
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            IyouTheme {
                IyouRoot(player)
            }
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
