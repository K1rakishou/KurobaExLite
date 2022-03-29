package com.github.k1rakishou.kurobaexlite.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
  tableName = ChanThreadViewEntity.TABLE_NAME
)
data class ChanThreadViewEntity(
  @PrimaryKey @Embedded(prefix = "thread_") val threadKey: ThreadKey,
  @Embedded(prefix = "last_viewed_") val lastViewedPost: ThreadLocalPostKey?,
  @Embedded(prefix = "last_loaded_") val lastLoadedPost: ThreadLocalPostKey?
) {
  companion object {
    const val TABLE_NAME = "chan_thread_view"
  }
}

@Dao
abstract class ChanThreadViewDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insert(chanThreadViewEntity: ChanThreadViewEntity)

  @Query("""
    SELECT * FROM chan_thread_view
    WHERE 
        thread_site_key = :siteKey
    AND
        thread_board_code = :boardCode
    AND
        thread_thread_no = :threadNo
  """)
  abstract suspend fun select(
    siteKey: String,
    boardCode: String,
    threadNo: Long
  ): ChanThreadViewEntity?
}