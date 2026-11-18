package com.bleepp.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** What Bleepp did with a notification, shown as a small stamp on its log entry. */
object AlertOutcome {
    const val NONE = 0
    const val RANG = 1
    const val BROKE_THROUGH = 2
    const val BLOCKED = 3
    const val CALL = 4
}

@Serializable
@Entity(tableName = "log", indices = [Index("postedAt"), Index("pkg")])
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The system's key for the notification, used to find it again while it's still showing. */
    val sbnKey: String,
    val pkg: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val outcome: Int = AlertOutcome.NONE,
    /** The app's notification channel, which is what a "feature" like Charging or Incoming call is. */
    @ColumnInfo(defaultValue = "''") val channelId: String = "",
    @ColumnInfo(defaultValue = "''") val channelName: String = "",
    /** Bookmarked entries are kept when old entries are cleaned up or the log is cleared. */
    @ColumnInfo(defaultValue = "0") val bookmarked: Boolean = false,
)

/** Sound id for calls that means "the phone's own ringtone". */
const val PHONE_RINGTONE = "ringtone"

@Serializable
@Entity(tableName = "rules")
data class AppRule(
    @PrimaryKey val pkg: String,
    val enabled: Boolean = true,
    /** A built-in sound id from [com.bleepp.alert.Sounds], or the content:// address of the user's own file. */
    val sound: String = "bleep",
    /** Percent of the stream's maximum. */
    val volume: Int = 70,
    val keywords: String = "",
    val breakthrough: Boolean = false,
    val repeat: Boolean = false,
    val repeatEverySeconds: Int = 30,
    val repeatMax: Int = 10,
    val vibrate: Boolean = true,
    /** Ring until answered, declined or missed when this app is ringing for a call. */
    @ColumnInfo(defaultValue = "1") val ringForCalls: Boolean = true,
    @ColumnInfo(defaultValue = "'$PHONE_RINGTONE'") val callSound: String = PHONE_RINGTONE,
)

data class LoggedApp(val pkg: String, val appName: String, val count: Int)

/** One feature of one app, as seen in the log. */
data class AppFeature(val pkg: String, val appName: String, val channelId: String, val channelName: String, val count: Int)

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Query(
        """SELECT * FROM log
           WHERE (:pkg IS NULL OR pkg = :pkg)
             AND (:savedOnly = 0 OR bookmarked = 1)
             AND (:query = '' OR title LIKE '%' || :query || '%' OR text LIKE '%' || :query || '%' OR appName LIKE '%' || :query || '%')
           ORDER BY postedAt DESC LIMIT :limit""",
    )
    fun observe(query: String, pkg: String?, savedOnly: Boolean, limit: Int): Flow<List<LogEntry>>

    @Query("SELECT pkg, appName, COUNT(*) AS count FROM log GROUP BY pkg ORDER BY count DESC")
    fun observeApps(): Flow<List<LoggedApp>>

    @Query(
        """SELECT pkg, appName, channelId, channelName, COUNT(*) AS count FROM log
           WHERE channelId != '' GROUP BY pkg, channelId ORDER BY appName, channelName""",
    )
    fun observeFeatures(): Flow<List<AppFeature>>

    @Query("SELECT COUNT(*) FROM log WHERE bookmarked = 1")
    fun observeSavedCount(): Flow<Int>

    @Query("SELECT * FROM log WHERE sbnKey = :key ORDER BY postedAt DESC LIMIT 1")
    suspend fun latestFor(key: String): LogEntry?

    @Query("SELECT * FROM log ORDER BY postedAt DESC")
    suspend fun all(): List<LogEntry>

    @Query("UPDATE log SET title = :title, text = :text, postedAt = :postedAt WHERE id = :id")
    suspend fun replaceText(id: Long, title: String, text: String, postedAt: Long)

    @Query("UPDATE log SET bookmarked = :bookmarked WHERE id IN (:ids)")
    suspend fun setBookmarked(ids: List<Long>, bookmarked: Boolean)

    @Query("DELETE FROM log WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(entries: List<LogEntry>)

    @Query("DELETE FROM log WHERE bookmarked = 0")
    suspend fun clearUnsaved()

    @Query("DELETE FROM log WHERE postedAt < :before AND bookmarked = 0")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM log WHERE pkg = :pkg AND channelId = :channelId AND bookmarked = 0")
    suspend fun deleteFeature(pkg: String, channelId: String)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules")
    fun observeAll(): Flow<List<AppRule>>

    @Query("SELECT * FROM rules")
    suspend fun all(): List<AppRule>

    @Query("SELECT * FROM rules WHERE pkg = :pkg")
    suspend fun get(pkg: String): AppRule?

    @Upsert
    suspend fun save(rule: AppRule)

    @Query("DELETE FROM rules WHERE pkg = :pkg")
    suspend fun delete(pkg: String)

    @Query("DELETE FROM rules")
    suspend fun clear()
}

@Entity(tableName = "excluded")
data class ExcludedApp(@PrimaryKey val pkg: String)

@Dao
interface ExcludedDao {
    @Query("SELECT pkg FROM excluded")
    fun observeAll(): Flow<List<String>>

    @Query("SELECT pkg FROM excluded")
    suspend fun all(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM excluded WHERE pkg = :pkg)")
    suspend fun isExcluded(pkg: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(app: ExcludedApp)

    @Query("DELETE FROM excluded WHERE pkg = :pkg")
    suspend fun remove(pkg: String)

    @Query("DELETE FROM excluded")
    suspend fun clear()
}

@Serializable
@Entity(tableName = "blocked_features", primaryKeys = ["pkg", "channelId"])
data class BlockedFeature(val pkg: String, val channelId: String, val channelName: String, val appName: String)

@Dao
interface BlockedFeatureDao {
    @Query("SELECT * FROM blocked_features ORDER BY appName, channelName")
    fun observeAll(): Flow<List<BlockedFeature>>

    @Query("SELECT * FROM blocked_features")
    suspend fun all(): List<BlockedFeature>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_features WHERE pkg = :pkg AND channelId = :channelId)")
    suspend fun isBlocked(pkg: String, channelId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(feature: BlockedFeature)

    @Query("DELETE FROM blocked_features WHERE pkg = :pkg AND channelId = :channelId")
    suspend fun remove(pkg: String, channelId: String)

    @Query("DELETE FROM blocked_features")
    suspend fun clear()
}

@Entity(tableName = "bookmarked_apps")
data class BookmarkedApp(@PrimaryKey val pkg: String)

@Dao
interface BookmarkedAppDao {
    @Query("SELECT pkg FROM bookmarked_apps")
    fun observeAll(): Flow<List<String>>

    @Query("SELECT pkg FROM bookmarked_apps")
    suspend fun all(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(app: BookmarkedApp)

    @Query("DELETE FROM bookmarked_apps WHERE pkg = :pkg")
    suspend fun remove(pkg: String)

    @Query("DELETE FROM bookmarked_apps")
    suspend fun clear()
}

/** 1.0 → 1.1: features and bookmarks in the log, call ringing in rules. Existing data is kept. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `log` ADD COLUMN `channelId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `log` ADD COLUMN `channelName` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `log` ADD COLUMN `bookmarked` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `rules` ADD COLUMN `ringForCalls` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `rules` ADD COLUMN `callSound` TEXT NOT NULL DEFAULT '$PHONE_RINGTONE'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `blocked_features` (`pkg` TEXT NOT NULL, `channelId` TEXT NOT NULL, " +
                "`channelName` TEXT NOT NULL, `appName` TEXT NOT NULL, PRIMARY KEY(`pkg`, `channelId`))",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarked_apps` (`pkg` TEXT NOT NULL, PRIMARY KEY(`pkg`))")
    }
}

@Database(
    entities = [LogEntry::class, AppRule::class, ExcludedApp::class, BlockedFeature::class, BookmarkedApp::class],
    version = 2,
)
abstract class BleeppDb : RoomDatabase() {
    abstract fun log(): LogDao
    abstract fun rules(): RuleDao
    abstract fun excluded(): ExcludedDao
    abstract fun blocked(): BlockedFeatureDao
    abstract fun bookmarkedApps(): BookmarkedAppDao

    companion object {
        @Volatile private var instance: BleeppDb? = null

        fun get(context: Context): BleeppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BleeppDb::class.java, "bleepp.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
