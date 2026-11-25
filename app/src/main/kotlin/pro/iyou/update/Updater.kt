package pro.iyou.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import pro.iyou.BuildConfig
import timber.log.Timber
import java.io.File

data class AppUpdate(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

/**
 * Looks at the GitHub release marked "latest" and, when it is newer than this build, downloads its
 * APK and hands it to Android's package installer. Because every release is signed with the same
 * key, the new version installs over the old one and keeps the library.
 */
class Updater(private val context: Context, private val http: OkHttpClient) {

    sealed interface Phase {
        data object Idle : Phase
        data object Checking : Phase
        data object UpToDate : Phase
        data class Downloading(val progress: Float) : Phase
        data object Installing : Phase
        data class Failed(val message: String) : Phase
    }

    @Volatile private var lastCheck = 0L
    private val _available = MutableStateFlow<AppUpdate?>(null)
    val available: StateFlow<AppUpdate?> = _available.asStateFlow()
    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    /** [force] = the user asked (or pulled to refresh); otherwise quietly, at most every 10 minutes. */
    suspend fun check(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (_phase.value is Phase.Downloading || _phase.value is Phase.Installing) return@withContext
        val now = System.currentTimeMillis()
        // quiet checks (app opened / back in front) only skip if one ran moments ago
        if (!force && now - lastCheck < MIN_GAP_MS) return@withContext
        lastCheck = now
        if (force) _phase.value = Phase.Checking
        runCatching {
            val request = Request.Builder().url(LATEST_RELEASE)
                .header("Accept", "application/vnd.github+json")
                .build()
            val body = http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) error("GitHub ${r.code}")
                r.body!!.string()
            }
            val release = Json.parseToJsonElement(body).jsonObject
            val tag = release["tag_name"]?.jsonPrimitive?.content.orEmpty()
            val asset = release["assets"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith("release.apk") == true }
                ?: error("Release $tag has no APK")
            AppUpdate(
                version = tag.removePrefix("v"),
                notes = release["body"]?.jsonPrimitive?.content.orEmpty(),
                apkUrl = asset["browser_download_url"]!!.jsonPrimitive.content,
                sizeBytes = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }.onSuccess { update ->
            val newer = isNewer(update.version, currentVersion)
            _available.value = update.takeIf { newer }
            _phase.value = if (newer || !force) Phase.Idle else Phase.UpToDate
        }.onFailure {
            Timber.w(it, "Update check failed")
            if (force) _phase.value = Phase.Failed("Gagal memeriksa update: ${it.message}")
        }
    }

    /** Android must allow this app to install apps; the first time, that means a trip to Settings. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(from: Context) {
        from.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
        )
    }

    suspend fun downloadAndInstall(update: AppUpdate) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val apk = File(dir, "iyou-${update.version}.apk")

            _phase.value = Phase.Downloading(0f)
            http.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { r ->
                if (!r.isSuccessful) error("Download ${r.code}")
                val body = r.body!!
                val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
                body.byteStream().use { input ->
                    apk.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            if (total > 0) _phase.value = Phase.Downloading(done.toFloat() / total)
                        }
                    }
                }
            }

            _phase.value = Phase.Installing
            install(apk)
        }.onFailure {
            Timber.w(it, "Update failed")
            _phase.value = Phase.Failed("Update gagal: ${it.message}")
        }
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("iyou.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        _phase.value = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Phase.Installing
            PackageInstaller.STATUS_SUCCESS -> Phase.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> Phase.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                Phase.Failed("Tanda tangan APK berbeda dengan yang terpasang. Uninstall Youtubro sekali, lalu pasang versi baru.")
            else -> Phase.Failed("Pemasangan gagal: ${message ?: status}")
        }
    }

    companion object {
        private const val LATEST_RELEASE = "https://api.github.com/repos/bozid95/youtubro/releases/latest"
        private const val MIN_GAP_MS = 10 * 60 * 1000L

        /** "1.10.0" > "1.9.2"; anything after a '-' (like "0.0.42-dev") is ignored. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

/** Android reports the install here; when it needs the user's OK, show its confirmation screen. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
        }
        (context.applicationContext as? pro.iyou.IyouApp)?.container?.updater?.onInstallResult(status, message)
    }
}
