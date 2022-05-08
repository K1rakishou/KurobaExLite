package com.github.k1rakishou.kurobaexlite.model.data.entity

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmark
import com.github.k1rakishou.kurobaexlite.model.data.local.bookmark.ThreadBookmarkReply
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import java.util.BitSet
import okhttp3.HttpUrl
import org.joda.time.DateTime

@Entity(
  tableName = ThreadBookmarkEntity.TABLE_NAME,
  indices = [
    Index(value = ["database_id"], unique = true)
  ]
)
data class ThreadBookmarkEntity(
  @PrimaryKey @Embedded(prefix = "bookmark_") val bookmarkKey: ThreadKey,
  @ColumnInfo(name = "database_id") val databaseId: Long = -1L,
  @ColumnInfo(name = "seen_posts_count") val seenPostsCount: Int? = null,
  @ColumnInfo(name = "total_posts_count") val totalPostsCount: Int? = null,
  @Embedded(prefix = "last_viewed_post_") val lastViewedPostKey: ThreadLocalPostKey? = null,
  @Embedded(prefix = "thread_last_post_") val threadLastPostKey: ThreadLocalPostKey? = null,
  @ColumnInfo(name = "title") val title: String? = null,
  @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: HttpUrl? = null,
  @ColumnInfo(name = "state") val state: BitSet,
  @ColumnInfo(name = "created_on") val createdOn: DateTime
) {

  companion object {
    const val TABLE_NAME = "thread_bookmarks"
  }
}

@Entity(
  tableName = ThreadBookmarkReplyEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ThreadBookmarkEntity::class,
      parentColumns = ["database_id"],
      childColumns = ["owner_database_id"],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ]
)
data class ThreadBookmarkReplyEntity(
  @PrimaryKey(autoGenerate = false) @ColumnInfo(name = "owner_database_id") val ownerDatabaseId: Long = 0L,
  @Embedded(prefix = "reply_post_") val replyPostKey: ThreadLocalPostKey,
  @Embedded(prefix = "reply_to_post_") val repliesToPostKey: ThreadLocalPostKey,
  @ColumnInfo(name = "already_seen") val alreadySeen: Boolean = false,
  @ColumnInfo(name = "already_notified") val alreadyNotified: Boolean = false,
  @ColumnInfo(name = "already_read") val alreadyRead: Boolean = false,
  @ColumnInfo(name = "time") val time: DateTime,
  @ColumnInfo(name = "comment_raw") val commentRaw: String?
) {

  companion object {
    const val TABLE_NAME = "thread_bookmark_replies"
  }
}

data class BookmarkKeyWithDatabaseId(
  @Embedded(prefix = "bookmark_") val threadKey: ThreadKey,
  @ColumnInfo(name = "database_id") val databaseId: Long,
)

data class ThreadBookmarkEntityWithReplies(
  @Embedded
  val threadBookmarkEntity: ThreadBookmarkEntity,
  @Relation(
    entity = ThreadBookmarkReplyEntity::class,
    parentColumn = "database_id",
    entityColumn = "owner_database_id"
  )
  val threadBookmarkEntityReplies: List<ThreadBookmarkReplyEntity>
)

@Dao
abstract class ThreadBookmarkDao {

  // Only load replies that are not seen/notified/read yet, otherwise there is no point in loading
  // them
  @Query("""
    SELECT *
    FROM thread_bookmarks
    LEFT OUTER JOIN thread_bookmark_replies
        ON thread_bookmarks.database_id = thread_bookmark_replies.owner_database_id
        AND (
          thread_bookmark_replies.already_read = ${KurobaExLiteDatabase.SQLITE_FALSE}
          OR 
          thread_bookmark_replies.already_notified = ${KurobaExLiteDatabase.SQLITE_FALSE}
          OR 
          thread_bookmark_replies.already_seen = ${KurobaExLiteDatabase.SQLITE_FALSE}
        )
  """)
  abstract suspend fun selectAllBookmarksWithReplies(): List<ThreadBookmarkEntityWithReplies>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertManyThreadBookmarkReplyEntities(
    threadBookmarkReplyEntityList: List<ThreadBookmarkReplyEntity>
  )

  @Query("""
    SELECT database_id
    FROM thread_bookmarks
    WHERE
        thread_bookmarks.bookmark_site_key = :siteKey
    AND
        thread_bookmarks.bookmark_board_code = :boardCode
    AND
        thread_bookmarks.bookmark_thread_no = :threadNo
  """)
  abstract suspend fun selectDatabaseIdByKey(
    siteKey: String,
    boardCode: String,
    threadNo: Long
  ): Long

  @Query("""
    SELECT 
      bookmark_site_key,
      bookmark_board_code,
      bookmark_thread_no,
      database_id
    FROM thread_bookmarks
  """)
  abstract suspend fun selectExistingKeys(): List<BookmarkKeyWithDatabaseId>

  @Query("""
    INSERT INTO thread_bookmarks(
        bookmark_site_key,
        bookmark_board_code,
        bookmark_thread_no,
        database_id,
        seen_posts_count,
        total_posts_count,
        last_viewed_post_post_no,
        last_viewed_post_post_sub_no,
        thread_last_post_post_no,
        thread_last_post_post_sub_no,
        title,
        thumbnail_url,
        state,
        created_on
    )
    VALUES(
        :siteKey,
        :boardCode,
        :threadNo,
        (SELECT IFNULL(MAX(database_id), 0) + 1 FROM thread_bookmarks),
        :seenPostsCount,
        :totalPostsCount,
        :lastViewedPostNo,
        :lastViewedPostSubNo,
        :threadLastPostNo,
        :threadLastPostSubNo,
        :title,
        :thumbnailUrl,
        :state,
        :createdOn
    )
  """)
  protected abstract suspend fun insertThreadBookmarkEntity(
    siteKey: String,
    boardCode: String,
    threadNo: Long,
    seenPostsCount: Int?,
    totalPostsCount: Int?,
    lastViewedPostNo: Long,
    lastViewedPostSubNo: Long,
    threadLastPostNo: Long,
    threadLastPostSubNo: Long,
    title: String?,
    thumbnailUrl: HttpUrl?,
    state: BitSet,
    createdOn: DateTime
  )

  @Transaction
  open suspend fun update(threadBookmarkEntity: ThreadBookmarkEntity) {
    updateInternal(
      siteKey = threadBookmarkEntity.bookmarkKey.siteKey,
      boardCode = threadBookmarkEntity.bookmarkKey.boardCode,
      threadNo = threadBookmarkEntity.bookmarkKey.threadNo,
      seenPostsCount = threadBookmarkEntity.seenPostsCount,
      totalPostsCount = threadBookmarkEntity.totalPostsCount,
      lastViewedPostNo = threadBookmarkEntity.lastViewedPostKey?.postNo ?: -1L,
      lastViewedPostSubNo = threadBookmarkEntity.lastViewedPostKey?.postSubNo ?: -1L,
      threadLastPostNo = threadBookmarkEntity.threadLastPostKey?.postNo ?: -1L,
      threadLastPostSubNo = threadBookmarkEntity.threadLastPostKey?.postSubNo ?: -1L,
      title = threadBookmarkEntity.title,
      thumbnailUrl = threadBookmarkEntity.thumbnailUrl,
      state = threadBookmarkEntity.state,
      createdOn = threadBookmarkEntity.createdOn,
    )
  }

  @Transaction
  open suspend fun updateMany(threadBookmarkEntityList: List<ThreadBookmarkEntity>) {
    threadBookmarkEntityList.forEach { threadBookmarkEntity ->
      updateInternal(
        siteKey = threadBookmarkEntity.bookmarkKey.siteKey,
        boardCode = threadBookmarkEntity.bookmarkKey.boardCode,
        threadNo = threadBookmarkEntity.bookmarkKey.threadNo,
        seenPostsCount = threadBookmarkEntity.seenPostsCount,
        totalPostsCount = threadBookmarkEntity.totalPostsCount,
        lastViewedPostNo = threadBookmarkEntity.lastViewedPostKey?.postNo ?: -1L,
        lastViewedPostSubNo = threadBookmarkEntity.lastViewedPostKey?.postSubNo ?: -1L,
        threadLastPostNo = threadBookmarkEntity.threadLastPostKey?.postNo ?: -1L,
        threadLastPostSubNo = threadBookmarkEntity.threadLastPostKey?.postSubNo ?: -1L,
        title = threadBookmarkEntity.title,
        thumbnailUrl = threadBookmarkEntity.thumbnailUrl,
        state = threadBookmarkEntity.state,
        createdOn = threadBookmarkEntity.createdOn,
      )
    }
  }

  @Query("""
    UPDATE OR FAIL
        thread_bookmarks
    SET
        seen_posts_count = :seenPostsCount,
        total_posts_count = :totalPostsCount,
        title = :title,
        thumbnail_url = :thumbnailUrl,
        state = :state,
        created_on = :createdOn,
        last_viewed_post_post_no = max(last_viewed_post_post_no, :lastViewedPostNo),
        last_viewed_post_post_sub_no = max(last_viewed_post_post_sub_no, :lastViewedPostSubNo),
        thread_last_post_post_no = max(thread_last_post_post_no, :threadLastPostNo),
        thread_last_post_post_sub_no = max(thread_last_post_post_sub_no, :threadLastPostSubNo)
    WHERE
        bookmark_site_key = :siteKey
    AND
        bookmark_board_code = :boardCode
    AND
        bookmark_thread_no = :threadNo
  """)
  protected abstract suspend fun updateInternal(
    siteKey: String,
    boardCode: String,
    threadNo: Long,
    seenPostsCount: Int?,
    totalPostsCount: Int?,
    title: String?,
    thumbnailUrl: HttpUrl?,
    state: BitSet,
    createdOn: DateTime,
    lastViewedPostNo: Long,
    lastViewedPostSubNo: Long,
    threadLastPostNo: Long,
    threadLastPostSubNo: Long
  )

  @Query("""
    DELETE FROM thread_bookmarks
    WHERE
        thread_bookmarks.bookmark_site_key = :siteKey
    AND
        thread_bookmarks.bookmark_board_code = :boardCode
    AND
        thread_bookmarks.bookmark_thread_no = :threadNo
  """)
  abstract suspend fun deleteBookmark(siteKey: String, boardCode: String, threadNo: Long)

  suspend fun insertOrUpdateBookmark(threadBookmark: ThreadBookmark) {
    insertOrUpdateManyBookmarks(listOf(threadBookmark))
  }

  suspend fun insertOrUpdateManyBookmarks(threadBookmarks: List<ThreadBookmark>) {
    val existingBookmarkKeys = selectExistingKeys()

    val existingBookmarkDescriptors = existingBookmarkKeys
      .map { bookmarkKeyWithDatabaseId -> bookmarkKeyWithDatabaseId.threadKey.threadDescriptor }
      .toSet()

    val databaseIdsMap = mutableMapWithCap<ThreadDescriptor, Long>(existingBookmarkKeys.size)
    existingBookmarkKeys.forEach { bookmarkKeyWithDatabaseId ->
      databaseIdsMap[bookmarkKeyWithDatabaseId.threadKey.threadDescriptor] = bookmarkKeyWithDatabaseId.databaseId
    }

    val toInsert = mutableListWithCap<ThreadBookmark>(threadBookmarks.size / 2)
    val toUpdate = mutableListWithCap<ThreadBookmark>(threadBookmarks.size / 2)

    threadBookmarks.forEach { threadBookmark ->
      if (threadBookmark.threadDescriptor in existingBookmarkDescriptors) {
        toUpdate += threadBookmark
      } else {
        toInsert += threadBookmark
      }
    }

    if (toInsert.isNotEmpty()) {
      toInsert.forEach { threadBookmark ->
        insertThreadBookmarkEntity(
          siteKey = threadBookmark.threadDescriptor.siteKey.key,
          boardCode = threadBookmark.threadDescriptor.boardCode,
          threadNo = threadBookmark.threadDescriptor.threadNo,
          seenPostsCount = threadBookmark.seenPostsCount,
          totalPostsCount = threadBookmark.totalPostsCount,
          lastViewedPostNo = threadBookmark.lastViewedPostPostDescriptor?.postNo ?: -1L,
          lastViewedPostSubNo = threadBookmark.lastViewedPostPostDescriptor?.postSubNo ?: -1L,
          threadLastPostNo = threadBookmark.lastPostDescriptorOfThread?.postNo ?: -1L,
          threadLastPostSubNo = threadBookmark.lastPostDescriptorOfThread?.postSubNo ?: -1L,
          title = threadBookmark.title,
          thumbnailUrl = threadBookmark.thumbnailUrl,
          state = threadBookmark.state,
          createdOn = threadBookmark.createdOn,
        )

        databaseIdsMap[threadBookmark.threadDescriptor] = selectDatabaseIdByKey(
          siteKey = threadBookmark.threadDescriptor.siteKey.key,
          boardCode = threadBookmark.threadDescriptor.boardCode,
          threadNo = threadBookmark.threadDescriptor.threadNo,
        )
      }
    }

    if (toUpdate.isNotEmpty()) {
      updateMany(toUpdate.map { it.toThreadBookmarkEntity() })
    }

    val threadBookmarkReplies = mutableMapWithCap<PostDescriptor, ThreadBookmarkReply>(
      initialCapacity = threadBookmarks.sumOf { it.threadBookmarkReplies.size }
    )
    threadBookmarks.forEach { threadBookmark -> threadBookmarkReplies.putAll(threadBookmark.threadBookmarkReplies) }

    if (threadBookmarkReplies.isNotEmpty()) {
      val threadBookmarkReplyEntityList = threadBookmarkReplies.entries.mapNotNull { (_, threadBookmarkReply) ->
        val databaseId = databaseIdsMap[threadBookmarkReply.postDescriptor.threadDescriptor]
          ?: return@mapNotNull null

        return@mapNotNull threadBookmarkReply.toThreadBookmarkReplyEntity(
          ownerDatabaseId = databaseId,
          threadBookmarkReply = threadBookmarkReply
        )
      }

      insertManyThreadBookmarkReplyEntities(threadBookmarkReplyEntityList)
    }
  }

}