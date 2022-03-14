package com.github.k1rakishou.kurobaexlite.database

import androidx.room.ColumnInfo
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

data class ThreadKey(
  @ColumnInfo(name = SITE_KEY) val siteKey: String,
  @ColumnInfo(name = BOARD_CODE) val boardCode: String,
  @ColumnInfo(name = THREAD_NO) val threadNo: Long
) {

  val threadDescriptor by lazy(mode = LazyThreadSafetyMode.NONE) {
    ThreadDescriptor.create(
      siteKey = SiteKey(siteKey),
      boardCode = boardCode,
      threadNo = threadNo
    )
  }

  companion object {
    fun fromThreadDescriptor(threadDescriptor: ThreadDescriptor): ThreadKey {
      return ThreadKey(
        siteKey = threadDescriptor.siteKeyActual,
        boardCode = threadDescriptor.boardCode,
        threadNo = threadDescriptor.threadNo
      )
    }

    const val SITE_KEY = "site_key"
    const val BOARD_CODE = "board_code"
    const val THREAD_NO = "thread_no"
  }

}

data class PostKey(
  @ColumnInfo(name = ThreadKey.SITE_KEY) val siteKey: String,
  @ColumnInfo(name = ThreadKey.BOARD_CODE) val boardCode: String,
  @ColumnInfo(name = ThreadKey.THREAD_NO) val threadNo: Long,
  @ColumnInfo(name = POST_NO) val postNo: Long,
  @ColumnInfo(name = POST_SUB_NO) val postSubNo: Long = 0
) {

  val postDescriptor by lazy(mode = LazyThreadSafetyMode.NONE) {
    PostDescriptor.create(
      siteKey = SiteKey(siteKey),
      boardCode = boardCode,
      threadNo = threadNo,
      postNo = postNo,
      postSubNo = postSubNo
    )
  }

  companion object {
    fun fromPostDescriptor(postDescriptor: PostDescriptor): PostKey {
      return PostKey(
        siteKey = postDescriptor.siteKeyActual,
        boardCode = postDescriptor.boardCode,
        threadNo = postDescriptor.threadNo,
        postNo = postDescriptor.postNo,
        postSubNo = postDescriptor.postSubNo,
      )
    }

    const val POST_NO = "post_no"
    const val POST_SUB_NO = "post_sub_no"
  }
}

data class ThreadLocalPostKey(
  @ColumnInfo(name = PostKey.POST_NO) val postNo: Long,
  @ColumnInfo(name = PostKey.POST_SUB_NO) val postSubNo: Long = 0
) {

  fun postDescriptor(threadDescriptor: ThreadDescriptor): PostDescriptor {
    return PostDescriptor.create(
      siteKey = threadDescriptor.siteKey,
      boardCode = threadDescriptor.boardCode,
      threadNo = threadDescriptor.threadNo,
      postNo = postNo,
      postSubNo = postSubNo
    )
  }

  companion object {
    fun fromPostDescriptor(postDescriptor: PostDescriptor): ThreadLocalPostKey {
      return ThreadLocalPostKey(
        postNo = postDescriptor.postNo,
        postSubNo = postDescriptor.postSubNo,
      )
    }
  }

}