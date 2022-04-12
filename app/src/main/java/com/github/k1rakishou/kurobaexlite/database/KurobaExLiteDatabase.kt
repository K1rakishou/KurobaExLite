package com.github.k1rakishou.kurobaexlite.database

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.github.k1rakishou.kurobaexlite.helpers.Try
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

@Database(
  entities = [
    ChanThreadViewEntity::class
  ],
  version = 1,
  exportSchema = true
)
abstract class KurobaExLiteDatabase : RoomDatabase(), Daos {
  abstract override val chanThreadViewDao: ChanThreadViewDao

  suspend fun <T : Any> transactionCall(func: suspend Daos.() -> T): Result<T> {
    return withContext(roomDispatcher) {
      return@withContext Result.Try { withTransaction { func(this@KurobaExLiteDatabase) } }
    }
  }

  suspend fun <T : Any> call(func: suspend Daos.() -> T): Result<T> {
    return withContext(roomDispatcher) {
      return@withContext Result.Try { func(this@KurobaExLiteDatabase) }
    }
  }

  companion object {
    internal val roomDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    const val DATABASE_NAME = "kuroba_ex_lite.db"
    const val EMPTY_JSON = "{}"

    // SQLite will thrown an exception if you attempt to pass more than 999 values into the IN
    // operator so we need to use batching to avoid this crash. And we use 950 instead of 999
    // just to be safe.
    const val SQLITE_IN_OPERATOR_MAX_BATCH_SIZE = 950
    const val SQLITE_TRUE = 1
    const val SQLITE_FALSE = 0

    fun buildDatabase(application: Application): KurobaExLiteDatabase {
      return Room.databaseBuilder(
        application.applicationContext,
        KurobaExLiteDatabase::class.java,
        DATABASE_NAME
      )
        .fallbackToDestructiveMigrationOnDowngrade()
        // TODO(KurobaEx): remove me once we hit the first alpha or something like that
        .fallbackToDestructiveMigration()
        .build()
    }
  }
}

interface Daos {
  val chanThreadViewDao: ChanThreadViewDao
}