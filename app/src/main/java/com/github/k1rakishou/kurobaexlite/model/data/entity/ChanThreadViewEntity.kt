package com.github.k1rakishou.kurobaexlite.model.data.entity

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
  @PrimaryKey @Embedded(prefix = "thread_") val catalogOrThreadKey: CatalogOrThreadKey,
  @Embedded(prefix = "last_viewed_for_indicator_") val lastViewedPostForIndicator: ThreadLocalPostKey?,
  @Embedded(prefix = "last_viewed_for_scroll_") val lastViewedPostForScroll: ThreadLocalPostKey?,
  @Embedded(prefix = "last_viewed_for_newPosts_") val lastViewedPostForNewPosts: ThreadLocalPostKey?,
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