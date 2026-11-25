package pro.iyou.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pro.iyou.R
import pro.iyou.app
import pro.iyou.update.UpdateControls
import pro.iyou.update.Updater
import pro.iyou.ui.theme.Palette

private const val REPO_URL = "https://github.com/bozid95/youtubro"
private const val RELEASES_URL = "$REPO_URL/releases"
private const val BLOG_URL = "https://widodo.my.id"

/** Tentang: version, update check / install, links and credits. */
@Composable
fun AboutScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val updater = context.app.container.updater
    val update by updater.available.collectAsState()
    val phase by updater.phase.collectAsState()
    val scope = rememberCoroutineScope()

    // opening the page is a good moment to look for a new version
    LaunchedEffect(Unit) { updater.check(force = true) }

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Text(
                "Tentang",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 24.dp),
            )
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(Palette.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_launcher_foreground), null,
                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                        modifier = Modifier.size(88.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Youtubro", style = MaterialTheme.typography.headlineSmall)
                Text("Versi ${updater.currentVersion}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            }
        }

        // update card
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.Surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val u = update
                val busy = phase is Updater.Phase.Downloading || phase is Updater.Phase.Installing
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        phase == Updater.Phase.Checking ->
                            CircularProgressIndicator(color = Palette.Text, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        u != null -> Icon(Icons.Rounded.NewReleases, null, tint = Palette.Accent)
                        else -> Icon(Icons.Rounded.CheckCircle, null, tint = Palette.TextMuted)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                phase == Updater.Phase.Checking -> "Memeriksa update…"
                                u != null -> "Versi baru ${u.version} tersedia"
                                else -> "Sudah versi terbaru"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (u != null && u.sizeBytes > 0) {
                            Text("%.1f MB".format(u.sizeBytes / 1_048_576f), style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                    }
                    if (!busy) {
                        IconButton(onClick = { scope.launch { updater.check(force = true) } }) {
                            Icon(Icons.Rounded.Refresh, "Periksa lagi", tint = Palette.TextMuted)
                        }
                    }
                }
                if (u != null) {
                    val notes = u.notes.lines()
                        .filter { it.isNotBlank() && !it.startsWith("**Full Changelog") }
                        .joinToString("\n")
                        .take(600)
                    if (notes.isNotBlank()) {
                        Text(notes, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                    UpdateControls(u)
                } else if (phase is Updater.Phase.Failed) {
                    Text((phase as Updater.Phase.Failed).message, style = MaterialTheme.typography.bodySmall, color = Palette.Accent)
                }
            }
        }

        item {
            Text(
                "Musik dan video YouTube tanpa iklan, tetap jalan di latar belakang. Tanpa akun: koleksi dan riwayat tersimpan di HP ini saja.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        item { LinkRow(Icons.Rounded.Code, "Kode sumber", "github.com/bozid95/youtubro") { open(REPO_URL) } }
        item { LinkRow(Icons.Rounded.NewReleases, "Semua rilis", "Riwayat versi & APK") { open(RELEASES_URL) } }
        item { LinkRow(Icons.Rounded.Language, "Blog", "widodo.my.id") { open(BLOG_URL) } }

        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Lisensi GPL-3.0", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                Text(
                    "Dibangun dengan Mochy Music, InnerTubeX dan NewPipeExtractor. Tidak berafiliasi dengan YouTube atau Google.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.TextMuted)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
    }
}
