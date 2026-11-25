package pro.iyou

import android.content.Intent
import android.net.Uri

/** Links iYou can open: YouTube / YouTube Music watch links, shared text, and `iyou://` links. */
sealed interface DeepLink {
    data class Play(val videoId: String) : DeepLink
    data class Search(val query: String) : DeepLink

    companion object {
        private val ID = Regex("^[A-Za-z0-9_-]{11}$")

        fun from(intent: Intent?): DeepLink? {
            intent ?: return null
            return when (intent.action) {
                Intent.ACTION_VIEW -> intent.data?.let(::fromUri)
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                    Regex("""https?://\S+""").find(text)?.value?.let { fromUri(Uri.parse(it)) }
                        ?: Search(text.trim()).takeIf { text.isNotBlank() }
                }
                else -> null
            }
        }

        fun fromUri(uri: Uri): DeepLink? {
            val host = uri.host.orEmpty()
            return when {
                uri.scheme == "iyou" && host == "search" -> uri.getQueryParameter("q")?.takeIf { it.isNotBlank() }?.let(::Search)
                uri.scheme == "iyou" && host == "play" -> uri.getQueryParameter("v")?.let(::play)
                host == "youtu.be" -> uri.lastPathSegment?.let(::play)
                host.endsWith("youtube.com") && uri.path?.startsWith("/shorts/") == true -> uri.lastPathSegment?.let(::play)
                host.endsWith("youtube.com") -> uri.getQueryParameter("v")?.let(::play)
                else -> null
            }
        }

        private fun play(id: String): Play? = id.takeIf { ID.matches(it) }?.let(::Play)
    }
}
