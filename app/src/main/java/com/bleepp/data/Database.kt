package com.bleepp.data

import android.content.Context
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
import kotlinx.coroutines.flow.Flow

/** What Bleepp did with a notification, shown as a small stamp on its log entry. */
object AlertOutcome {
    const val NONE = 0
    const val RANG = 1
    const val BROKE_THROUGH = 2
    const val BLOCKED = 3
}

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
)

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
)

data class LoggedApp(val pkg: String, val appName: String, val count: Int)

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Query(
        """SELECT * FROM log
           WHERE (:pkg IS NULL OR pkg = :pkg)
             AND (:query = '' OR title LIKE '%' || :query || '%' OR text LIKE '%' || :query || '%' OR appName LIKE '%' || :query || '%')
           ORDER BY postedAt DESC LIMIT :limit""",
    )
    fun observe(query: String, pkg: String?, limit: Int): Flow<List<LogEntry>>

    @Query("SELECT pkg, appName, COUNT(*) AS count FROM log GROUP BY pkg ORDER BY count DESC")
    fun observeApps(): Flow<List<LoggedApp>>

    @Query("SELECT * FROM log WHERE sbnKey = :key ORDER BY postedAt DESC LIMIT 1")
    suspend fun latestFor(key: String): LogEntry?

    @Query("UPDATE log SET outcome = :outcome WHERE id = :id")
    suspend fun setOutcome(id: Long, outcome: Int)

    @Query("DELETE FROM log WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM log")
    suspend fun clear()

    @Query("DELETE FROM log WHERE postedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules")
    fun observeAll(): Flow<List<AppRule>>

    @Query("SELECT * FROM rules WHERE pkg = :pkg")
    suspend fun get(pkg: String): AppRule?

    @Upsert
    suspend fun save(rule: AppRule)

    @Query("DELETE FROM rules WHERE pkg = :pkg")
    suspend fun delete(pkg: String)
}

@Entity(tableName = "excluded")
data class ExcludedApp(@PrimaryKey val pkg: String)

@Dao
interface ExcludedDao {
    @Query("SELECT pkg FROM excluded")
    fun observeAll(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM excluded WHERE pkg = :pkg)")
    suspend fun isExcluded(pkg: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(app: ExcludedApp)

    @Query("DELETE FROM excluded WHERE pkg = :pkg")
    suspend fun remove(pkg: String)
}

@Database(entities = [LogEntry::class, AppRule::class, ExcludedApp::class], version = 1)
abstract class BleeppDb : RoomDatabase() {
    abstract fun log(): LogDao
    abstract fun rules(): RuleDao
    abstract fun excluded(): ExcludedDao

    companion object {
        @Volatile private var instance: BleeppDb? = null

        fun get(context: Context): BleeppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BleeppDb::class.java, "bleepp.db")
                .build().also { instance = it }
        }
    }
}
