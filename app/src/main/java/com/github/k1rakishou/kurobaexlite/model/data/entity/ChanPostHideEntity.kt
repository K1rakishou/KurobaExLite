package com.github.k1rakishou.kurobaexlite.model.data.entity

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

@Entity(
  tableName = ChanPostHideEntity.TABLE_NAME
)
data class ChanPostHideEntity(
  @PrimaryKey @Embedded(prefix = "post_hide_") val postKey: PostKey,
  @ColumnInfo(name = "apply_to_replies") val applyToReplies: Boolean,
  @ColumnInfo(name = "state") val state: Int,
  @ColumnInfo(name = "inserted_on") val insertedOn: Long
) {

  companion object {
    const val TABLE_NAME = "chan_post_hides"

    const val Unspecified = 0
    const val HiddenManually = 1
    const val UnhiddenManually = 2
  }
}

@Entity(
  tableName = ChanPostHideReplyEntity.TABLE_NAME,
  primaryKeys = [
    "owner_post_hide_site_key",
    "owner_post_hide_board_code",
    "owner_post_hide_thread_no",
    "owner_post_hide_post_no",
    "owner_post_hide_post_sub_no",
    "replies_to_hidden_post_site_key",
    "replies_to_hidden_post_board_code",
    "replies_to_hidden_post_thread_no",
    "replies_to_hidden_post_post_no",
    "replies_to_hidden_post_post_sub_no",
  ],
  foreignKeys = [
    ForeignKey(
      entity = ChanPostHideEntity::class,
      parentColumns = [
        "post_hide_site_key",
        "post_hide_board_code",
        "post_hide_thread_no",
        "post_hide_post_no",
        "post_hide_post_sub_no"
      ],
      childColumns = [
        "owner_post_hide_site_key",
        "owner_post_hide_board_code",
        "owner_post_hide_thread_no",
        "owner_post_hide_post_no",
        "owner_post_hide_post_sub_no"
      ],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ]
)
data class ChanPostHideReplyEntity(
  @Embedded(prefix = "owner_post_hide_") val ownerPostKey: PostKey,
  @Embedded(prefix = "replies_to_hidden_post_") val repliesToHiddenPost: PostKey
) {

  companion object {
    const val TABLE_NAME = "chan_post_hide_replies"
  }
}

@Dao
abstract class ChanPostHideReplyDao {
  @Query("""
    SELECT *
    FROM ${ChanPostHideReplyEntity.TABLE_NAME}
      WHERE
        owner_post_hide_site_key = :siteKey
    AND
        owner_post_hide_board_code = :boardCode
    AND
        owner_post_hide_thread_no = :threadNo
    AND
        owner_post_hide_post_no = :postNo
    AND
        owner_post_hide_post_sub_no = :postSubNo
  """)
  abstract suspend fun selectForPost(
    siteKey: String,
    boardCode: String,
    threadNo: Long,
    postNo: Long,
    postSubNo: Long
  ): List<ChanPostHideReplyEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertOrUpdateMany(chanPostHideReplyEntities: List<ChanPostHideReplyEntity>)
}

@Dao
abstract class ChanPostHideDao {
  @Query("""
    SELECT *
    FROM ${ChanPostHideEntity.TABLE_NAME}
    WHERE
        post_hide_site_key = :siteKey
    AND
        post_hide_board_code = :boardCode
    AND
        post_hide_thread_no = :threadNo
  """)
  abstract suspend fun selectAllForThread(siteKey: String, boardCode: String, threadNo: Long): List<ChanPostHideEntity>

  @Query("""
    SELECT *
    FROM ${ChanPostHideEntity.TABLE_NAME}
    WHERE
        post_hide_site_key = :siteKey
    AND
        post_hide_board_code = :boardCode
    AND
        post_hide_thread_no IN (:threadNos)
  """)
  abstract suspend fun selectAllForCatalog(siteKey: String, boardCode: String, threadNos: Collection<Long>): List<ChanPostHideEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertOrUpdateMany(chanPostHideEntities: Collection<ChanPostHideEntity>)

  @Update(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun updateMany(chanPostHideEntities: Collection<ChanPostHideEntity>)

  @Query("""
    DELETE FROM ${ChanPostHideEntity.TABLE_NAME}
    WHERE
        post_hide_site_key = :siteKey
    AND
        post_hide_board_code = :boardCode
    AND
        post_hide_thread_no = :threadNo
    AND
        post_hide_post_no = :postNo
    AND
        post_hide_post_sub_no = :postSubNo
  """)
  abstract suspend fun delete(siteKey: String, boardCode: String, threadNo: Long, postNo: Long, postSubNo: Long)

  @Query("""
    DELETE FROM ${ChanPostHideEntity.TABLE_NAME}
    WHERE inserted_on < :timestamp
  """)
  abstract suspend fun deleteOlderThan(timestamp: Long): Int
}