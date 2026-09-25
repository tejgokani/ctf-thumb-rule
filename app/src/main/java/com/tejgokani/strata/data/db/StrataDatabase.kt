package com.tejgokani.strata.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File

/**
 * Room hardening (plan §7): the platform default for a corrupted database is to DELETE it
 * (`SupportSQLiteOpenHelper.Callback.onCorruption()`'s default implementation calls
 * `deleteDatabaseFile`) — for an app whose local DB is supposed to be a rebuildable *cache*
 * (plan §4), silently destroying it on first sign of corruption throws away the fast path and
 * forces every future launch through full Drive recovery with no diagnostic trail.
 *
 * [QuarantineOpenHelperFactory] intercepts onCorruption and renames the file aside instead,
 * leaving the corrupt bytes available for inspection and letting [MainActivity]/[StrataApp]
 * detect "this is a freshly recreated, empty database" and trigger RecoveryEngine explicitly,
 * rather than silently proceeding as if there had never been any data.
 *
 * `fallbackToDestructiveMigration()` is never used here — every schema change must ship an
 * explicit [androidx.room.migration.Migration].
 */
class QuarantineOpenHelperFactory(
    private val onQuarantined: (quarantinedPath: String) -> Unit,
) : SupportSQLiteOpenHelper.Factory {
    private val delegate = FrameworkSQLiteOpenHelperFactory()

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val original = configuration.callback
        val wrapped = object : SupportSQLiteOpenHelper.Callback(original.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = original.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = original.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                original.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                original.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = original.onOpen(db)

            override fun onCorruption(db: SupportSQLiteDatabase) {
                // Deliberately does NOT delegate to original.onCorruption() / super.onCorruption(),
                // both of which delete the file. Quarantine instead — see class doc.
                val path = db.path
                try { db.close() } catch (_: Exception) { /* already unusable */ }
                if (path != null) {
                    val src = File(path)
                    if (src.exists()) {
                        val dest = File(src.parentFile, "${src.name}.corrupt-${System.currentTimeMillis()}")
                        src.renameTo(dest)
                    }
                    onQuarantined(path)
                }
            }
        }
        val newConfig = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
            .name(configuration.name)
            .callback(wrapped)
            .noBackupDirectory(configuration.useNoBackupDirectory)
            .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)
            .build()
        return delegate.create(newConfig)
    }
}

@Database(
    entities = [
        AccountEntity::class, FileEntity::class, ChunkEntity::class,
        PlacementEntity::class, ReservationEntity::class, LedgerEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class StrataDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun fileDao(): FileDao
    abstract fun chunkDao(): ChunkDao
    abstract fun placementDao(): PlacementDao
    abstract fun reservationDao(): ReservationDao
    abstract fun ledgerDao(): LedgerDao

    /** Returns true iff SQLite reports the file structurally intact. Run once at startup (plan §7). */
    suspend fun runIntegrityCheck(): Boolean {
        val cursor = query("PRAGMA integrity_check", emptyArray())
        return cursor.use { it.moveToFirst() && it.getString(0).equals("ok", ignoreCase = true) }
    }

    companion object {
        const val DB_NAME = "strata.db"

        @Volatile private var instance: StrataDatabase? = null
        @Volatile var wasQuarantinedThisLaunch: Boolean = false
            private set

        fun get(context: Context): StrataDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context): StrataDatabase =
            Room.databaseBuilder(context.applicationContext, StrataDatabase::class.java, DB_NAME)
                .openHelperFactory(QuarantineOpenHelperFactory { path ->
                    wasQuarantinedThisLaunch = true
                    android.util.Log.e("Strata", "Database at $path was corrupt and has been quarantined, not deleted.")
                })
                // No fallbackToDestructiveMigration(): every future schema change must add a
                // real Migration, or Room will (correctly) refuse to open an old database.
                .build()
    }
}
