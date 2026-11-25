package pro.iyou.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pro.iyou.app
import pro.iyou.ui.theme.Palette

/** Card at the top of Home when GitHub has a newer release. Nothing when up to date. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val updater = context.app.container.updater
    val update by updater.available.collectAsState()
    val phase by updater.phase.collectAsState()
    val scope = rememberCoroutineScope()
    val u = update ?: return

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.SurfaceHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = Palette.Accent, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text("Versi baru ${u.version} tersedia", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Terpasang: ${updater.currentVersion}" + if (u.sizeBytes > 0) " · %.1f MB".format(u.sizeBytes / 1_048_576f) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
        }
        if (u.notes.isNotBlank()) {
            Text(
                u.notes.lines().filter { it.isNotBlank() && !it.startsWith("**Full Changelog") }.joinToString("\n").take(400),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (val p = phase) {
            is Updater.Phase.Downloading -> {
                LinearProgressIndicator(
                    progress = { p.progress },
                    color = Palette.Accent,
                    trackColor = Palette.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Mengunduh… ${(p.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            Updater.Phase.Installing -> Text("Memasang… ikuti konfirmasi dari Android.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            else -> {
                if (p is Updater.Phase.Failed) {
                    Text(p.message, style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
                }
                val allowed = updater.canInstall()
                Button(
                    onClick = {
                        if (!updater.canInstall()) updater.openInstallPermissionSettings(context)
                        else scope.launch { updater.downloadAndInstall(u) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Text, contentColor = Palette.Background),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (allowed) "Download & update" else "Izinkan pemasangan, lalu update")
                }
            }
        }
    }
}

/** Version line with a manual "check now", for the bottom of Koleksi. */
@Composable
fun VersionFooter(modifier: Modifier = Modifier) {
    val updater = LocalContext.current.app.container.updater
    val phase by updater.phase.collectAsState()
    val update by updater.available.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Youtubro ${updater.currentVersion}", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        val status = when (val p = phase) {
            Updater.Phase.Checking -> "Memeriksa…"
            Updater.Phase.UpToDate -> "Sudah versi terbaru"
            is Updater.Phase.Failed -> p.message
            else -> update?.let { "Versi ${it.version} tersedia, lihat Beranda" }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted) }
        TextButton(onClick = { scope.launch { updater.check(force = true) } }) {
            Text("Periksa update", color = Palette.Text)
        }
    }
}
