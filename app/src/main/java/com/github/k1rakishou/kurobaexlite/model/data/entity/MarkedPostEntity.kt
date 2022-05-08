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
    const val TABLE_NAME = "marked_posts"
  }
}

@Dao
abstract class MarkedPostDao {

  @Query("""
    SELECT * FROM marked_posts
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
    threadNo: Long,
  ): List<MarkedPostEntity>

  @Query("""
    SELECT * FROM marked_posts
    WHERE 
        marked_post_site_key = :siteKey
    AND
        marked_post_board_code = :boardCode
    AND
        marked_post_thread_no = :threadNo
    AND
        type = :type
  """)
  abstract suspend fun selectWithType(
    siteKey: String,
    boardCode: String,
    threadNo: Long,
    type: Int
  ): List<MarkedPostEntity>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insert(markedPostEntity: MarkedPostEntity)

  @Query("""
    DELETE FROM marked_posts
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