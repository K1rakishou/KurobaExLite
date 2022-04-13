package com.github.k1rakishou.kurobaexlite.model.database

import androidx.room.ColumnInfo
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

data class CatalogOrThreadKey(
  @ColumnInfo(name = SITE_KEY) val siteKey: String,
  @ColumnInfo(name = BOARD_CODE) val boardCode: String,
  @ColumnInfo(name = THREAD_NO) val threadNo: Long
) {

  val threadDescriptor by lazy(mode = LazyThreadSafetyMode.NONE) {
    check(threadNo > 0L) { "Bad threadNo: ${threadNo}" }

    return@lazy ThreadDescriptor.create(
      siteKey = SiteKey(siteKey),
      boardCode = boardCode,
      threadNo = threadNo
    )
  }

  val catalogDescriptor by lazy(mode = LazyThreadSafetyMode.NONE) {
    check(threadNo == -1L) { "Bad threadNo: ${threadNo}" }

    return@lazy CatalogDescriptor(
      siteKey = SiteKey(siteKey),
      boardCode = boardCode
    )
  }

  val chanDescriptor: ChanDescriptor
    get() {
      if (threadNo < 0L) {
        return catalogDescriptor
      }

      return threadDescriptor
    }

  companion object {
    fun fromChanDescriptor(chanDescriptor: ChanDescriptor): CatalogOrThreadKey {
      return when (chanDescriptor) {
        is CatalogDescriptor -> fromCatalogDescriptor(chanDescriptor)
        is ThreadDescriptor -> fromThreadDescriptor(chanDescriptor)
      }
    }

    fun fromThreadDescriptor(threadDescriptor: ThreadDescriptor): CatalogOrThreadKey {
      return CatalogOrThreadKey(
        siteKey = threadDescriptor.siteKeyActual,
        boardCode = threadDescriptor.boardCode,
        threadNo = threadDescriptor.threadNo
      )
    }

    fun fromCatalogDescriptor(catalogDescriptor: CatalogDescriptor): CatalogOrThreadKey {
      return CatalogOrThreadKey(
        siteKey = catalogDescriptor.siteKeyActual,
        boardCode = catalogDescriptor.boardCode,
        threadNo = -1L
      )
    }

    const val SITE_KEY = "site_key"
    const val BOARD_CODE = "board_code"
    const val THREAD_NO = "thread_no"
  }

}

data class PostKey(
  @ColumnInfo(name = CatalogOrThreadKey.SITE_KEY) val siteKey: String,
  @ColumnInfo(name = CatalogOrThreadKey.BOARD_CODE) val boardCode: String,
  @ColumnInfo(name = CatalogOrThreadKey.THREAD_NO) val threadNo: Long,
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