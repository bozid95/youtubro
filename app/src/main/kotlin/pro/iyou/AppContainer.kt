package pro.iyou

import android.content.Context
import androidx.room.Room
import okhttp3.OkHttpClient
import pro.iyou.data.MusicRepository
import pro.iyou.data.Settings
import pro.iyou.data.db.AppDatabase
import pro.iyou.data.stream.StreamResolver
import java.util.concurrent.TimeUnit

/** Hand-rolled DI: one of each, created once in [IyouApp]. */
class AppContainer(context: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "iyou.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val repository = MusicRepository(db.songs())
    val streams = StreamResolver(context, http)
    val settings = Settings(context)
}
