package ir.pingscore.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** یک ردیف تاریخچه. ستون‌های نمره برای نمودار و مرتب‌سازی؛ جزئیات کامل در reportJson. */
@Entity(tableName = "tests")
data class TestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    /** نام اتصال (قابل ویرایش توسط کاربر). */
    val name: String,
    val mode: String,
    val source: String,
    val transport: String,
    val viaTunnel: Boolean,
    val overall: Int,
    val aiScore: Int,
    val videoScore: Int,
    val chatScore: Int,
    /** تست مبنا (اتصال پایه)؛ حداکثر یکی. */
    val isBaseline: Boolean = false,
    val reportJson: String,
)

@Dao
abstract class TestDao {

    @Insert
    abstract suspend fun insert(entity: TestEntity): Long

    @Query("SELECT * FROM tests ORDER BY timeMillis DESC")
    abstract fun observeAll(): Flow<List<TestEntity>>

    @Query("SELECT * FROM tests WHERE id = :id")
    abstract fun observe(id: Long): Flow<TestEntity?>

    @Query("SELECT * FROM tests WHERE id IN (:ids)")
    abstract suspend fun getByIds(ids: List<Long>): List<TestEntity>

    @Query("UPDATE tests SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("UPDATE tests SET isBaseline = 0 WHERE isBaseline = 1")
    abstract suspend fun clearBaseline()

    @Query("UPDATE tests SET isBaseline = 1 WHERE id = :id")
    abstract suspend fun markBaseline(id: Long)

    @Transaction
    open suspend fun setBaseline(id: Long) {
        clearBaseline()
        markBaseline(id)
    }

    @Query("DELETE FROM tests WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM tests")
    abstract suspend fun deleteAll()

    /** فقط جدیدترین [keep] ردیف (به‌جز مبنا) نگه داشته می‌شود تا حجم دیتابیس زیاد نشود. */
    @Query(
        "DELETE FROM tests WHERE isBaseline = 0 AND id NOT IN " +
            "(SELECT id FROM tests ORDER BY timeMillis DESC LIMIT :keep)",
    )
    abstract suspend fun prune(keep: Int)
}

@Database(entities = [TestEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun testDao(): TestDao
}

object AppGraph {
    @Volatile
    private var db: AppDatabase? = null

    fun db(context: Context): AppDatabase =
        db ?: synchronized(this) {
            db ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pingscore.db")
                .build()
                .also { db = it }
        }
}
