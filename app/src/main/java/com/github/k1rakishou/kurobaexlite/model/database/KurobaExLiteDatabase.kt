package com.github.k1rakishou.kurobaexlite.model.database

import android.app.Application
import androidx.annotation.CheckResult
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanCatalogDao
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanCatalogEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanCatalogFlagDao
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanCatalogFlagEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanCatalogSortOrderEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanPostHideDao
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanPostHideEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanPostHideReplyDao
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanPostHideReplyEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanThreadViewDao
import com.github.k1rakishou.kurobaexlite.model.data.entity.ChanThreadViewEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.MarkedPostDao
import com.github.k1rakishou.kurobaexlite.model.data.entity.MarkedPostEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.NavigationHistoryDao
import com.github.k1rakishou.kurobaexlite.model.data.entity.NavigationHistoryEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ThreadBookmarkDao
import com.github.k1rakishou.kurobaexlite.model.data.entity.ThreadBookmarkEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ThreadBookmarkReplyEntity
import com.github.k1rakishou.kurobaexlite.model.data.entity.ThreadBookmarkSortOrderEntity
import com.github.k1rakishou.kurobaexlite.model.database.converters.BitSetTypeConverter
import com.github.k1rakishou.kurobaexlite.model.database.converters.DateTimeTypeConverter
import com.github.k1rakishou.kurobaexlite.model.database.converters.HttpUrlTypeConverter
import com.github.k1rakishou.kurobaexlite.model.database.migations.Migration_v1_to_v2
import com.github.k1rakishou.kurobaexlite.model.database.migations.Migration_v2_to_v3
import com.github.k1rakishou.kurobaexlite.model.database.migations.Migration_v3_to_v4
import com.github.k1rakishou.kurobaexlite.model.database.migations.Migration_v4_to_v5
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
  entities = [
    ChanThreadViewEntity::class,
    NavigationHistoryEntity::class,
    MarkedPostEntity::class,
    ChanCatalogEntity::class,
    ChanCatalogSortOrderEntity::class,
    ThreadBookmarkEntity::class,
    ThreadBookmarkReplyEntity::class,
    ThreadBookmarkSortOrderEntity::class,
    ChanCatalogFlagEntity::class,
    ChanPostHideEntity::class,
    ChanPostHideReplyEntity::class,
  ],
  version = 5,
  exportSchema = true
)
@TypeConverters(
  value = [
    DateTimeTypeConverter::class,
    HttpUrlTypeConverter::class,
    BitSetTypeConverter::class,
  ]
)
abstract class KurobaExLiteDatabase : RoomDatabase(), Daos {

  @CheckResult
  suspend fun <T> transaction(func: suspend Daos.() -> T): Result<T> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try { withTransaction { func(this@KurobaExLiteDatabase) } }
    }
  }

  @CheckResult
  suspend fun <T> call(func: suspend Daos.() -> T): Result<T> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try { func(this@KurobaExLiteDatabase) }
    }
  }

  companion object {
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
        .addMigrations(
          Migration_v1_to_v2(),
          Migration_v2_to_v3(),
          Migration_v3_to_v4(),
          Migration_v4_to_v5(),
        )
        .build()
    }
  }
}

interface Daos {
  val chanThreadViewDao: ChanThreadViewDao
  val navigationHistoryDao: NavigationHistoryDao
  val markedPostDao: MarkedPostDao
  val chanCatalogDao: ChanCatalogDao
  val threadBookmarkDao: ThreadBookmarkDao
  val chanCatalogFlagDao: ChanCatalogFlagDao
  val chanPostHideDao: ChanPostHideDao
  val chanPostHideReplyDao: ChanPostHideReplyDao
}