# iYou

Pemutar musik YouTube Music untuk Android yang minimalis: **tanpa iklan**, **tetap jalan di latar belakang** (layar mati / aplikasi diminimalkan), dan tanpa akun.

## Fitur

- **Beranda**: rekomendasi YouTube Music (Quick picks, album, playlist), tarik untuk refresh
- **Cari**: saran pencarian, riwayat pencarian, filter Lagu / Video / Album / Playlist
- **Pemutar**: warna latar mengikuti cover, seek, acak, ulangi, antrean (lompat / hapus)
- **Mini player** yang mengambang di atas tab
- **Radio otomatis**: memutar satu lagu langsung mengisi antrean dengan lagu terkait
- **Koleksi**: lagu disukai dan riwayat putar, tersimpan lokal di HP
- **Background playback** + kontrol di notifikasi dan lock screen (Media3 `MediaSessionService`)
- Cache audio 512 MB, jadi lagu yang sudah diputar tidak diunduh ulang

## Arsitektur

```
app/
  data/stream/      StreamResolver: InnerTubeX → client walk (VISIONOS, WEB_REMIX + PoToken) → NewPipe
  data/stream/potoken  BotGuard PoToken lewat WebView (port dari Mochy)
  data/             MusicRepository (home, search, album/playlist, radio) + Room (disukai, riwayat)
  playback/         PlaybackService (ExoPlayer + MediaSession), PlayerConnection (MediaController → StateFlow)
  ui/               Jetpack Compose: Beranda, Cari, Koleksi, Album/Playlist, Player
innertube/          Modul InnerTube dari Mochy Music (tidak diubah)
```

Stack: Kotlin, Jetpack Compose + Material 3, Media3 ExoPlayer, Ktor, Room, Coil 3, InnerTubeX, NewPipeExtractor.

## Build

**Lewat GitHub Actions:** setiap push ke `main`, workflow [Build APK](.github/workflows/build.yml) membangun APK debug dan release. Unduh dari tab **Actions → run terbaru → Artifacts → `iyou-apk`**. Kalau push tag `v*` (misalnya `v0.1.0`), APK juga dilampirkan ke GitHub Release.

Supaya APK release bisa di-update tanpa uninstall, tambahkan secrets berikut di repo (Settings → Secrets → Actions):
`KEYSTORE_BASE64` (`base64 -w0 kunci.jks`), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
Tanpa secrets ini, APK release ditandatangani dengan debug key milik runner, yang berubah di setiap build.

**Lokal:** buka di Android Studio (JDK 21 bawaan), sync, lalu Run. Atau `./gradlew assembleDebug`.

## Catatan

- YouTube sering mengubah cara streaming. Kalau lagu tiba-tiba gagal diputar, coba naikkan versi `innertubex` dan `newpipeextractor` di [gradle/libs.versions.toml](gradle/libs.versions.toml), atau ambil perubahan terbaru dari Mochy.
- Background play dan pemblokiran iklan melanggar Ketentuan Layanan YouTube. Aplikasi ini **tidak bisa dirilis di Play Store** dan ditujukan untuk penggunaan pribadi.
- Tidak berafiliasi dengan YouTube atau Google.

## Lisensi & kredit

GPL-3.0, lihat [LICENSE](LICENSE). Lapisan data (modul `innertube`, PoToken, dan pola resolusi stream) diambil dari [Mochy Music](https://github.com/realidkroo/Mochy-Music) oleh realidkroo, yang juga berlisensi GPL-3.0 dan berbasis ViviMusic / Metrolist. Pencarian stream memakai [InnerTubeX](https://github.com/MetrolistGroup/innertubex) dan [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).
