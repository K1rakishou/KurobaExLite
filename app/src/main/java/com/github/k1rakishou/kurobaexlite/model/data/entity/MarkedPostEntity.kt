package com.github.k1rakishou.kurobaexlite.model.data.entity

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = MarkedPostEntity.TABLE_NAME)
class MarkedPostEntity(
  @PrimaryKey @Embedded(prefix = "marked_post_") val postKey: PostKey,
  val type: Int
) {

  companion object {
    const val TABLE_NAME = "mark_post"
  }
}

@Dao
abstract class MarkedPostDao {

  @Query("""
    SELECT * FROM mark_post
    WHERE 
        marked_post_site_key = :siteKey
    AND
        marked_post_board_code = :boardCode
    AND
        marked_post_thread_no = :threadNo
  """)
  abstract suspend fun select(
    siteKey: String,
    boardCode: String,
    threadNo: Long
  ): List<MarkedPostEntity>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insert(markedPostEntity: MarkedPostEntity)

  @Query("""
    DELETE FROM mark_post
    WHERE 
        marked_post_site_key = :siteKey
    AND
        marked_post_board_code = :boardCode
    AND
        marked_post_thread_no = :threadNo
    AND
        marked_post_post_no = :postNo
    AND
        marked_post_post_sub_no = :postSubNo
  """)
  abstract suspend fun delete(
    siteKey: String,
    boardCode: String,
    threadNo: Long,
    postNo: Long,
    postSubNo: Long
  )

}