package com.github.k1rakishou.kurobaexlite.model.descriptors

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
inline class SiteKey(val key: String) : Parcelable {

  override fun toString(): String {
    return buildString(capacity = 32) {
      append("SiteKey(")
      append(key)
      append(")")
    }
  }

}

sealed class ChanDescriptor

@Parcelize
data class CatalogDescriptor(
  val siteKey: SiteKey,
  val boardCode: String
) : Parcelable, ChanDescriptor() {

  override fun toString(): String {
    return buildString(capacity = 32) {
      append("CatalogDescriptor(")
      append(siteKey)
      append("/")
      append(boardCode)
      append(")")
    }
  }

}

@Parcelize
data class ThreadDescriptor(
  val catalogDescriptor: CatalogDescriptor,
  val threadNo: Long
) : Parcelable, ChanDescriptor() {
  val siteKey: SiteKey
    get() = catalogDescriptor.siteKey
  val siteKeyActual: String
    get() = siteKey.key
  val boardCode: String
    get() = catalogDescriptor.boardCode

  override fun toString(): String {
    return buildString(capacity = 32) {
      append("ThreadDescriptor(")
      append(siteKeyActual)
      append("/")
      append(boardCode)
      append("/")
      append(threadNo)
      append(")")
    }
  }

  companion object {
    fun create(siteKey: SiteKey, boardCode: String, threadNo: Long): ThreadDescriptor {
      require(threadNo > 0L) { "Bad threadNo: $threadNo" }

      return ThreadDescriptor(
        catalogDescriptor = CatalogDescriptor(
          siteKey = siteKey,
          boardCode = boardCode
        ),
        threadNo = threadNo
      )
    }
  }

}

@Parcelize
data class PostDescriptor(
  val threadDescriptor: ThreadDescriptor,
  val postNo: Long,
  val postSubNo: Long? = null
) : Parcelable {
  val siteKeyActual: String
    get() = threadDescriptor.catalogDescriptor.siteKey.key
  val siteKey: SiteKey
    get() = threadDescriptor.catalogDescriptor.siteKey
  val boardCode: String
    get() = threadDescriptor.catalogDescriptor.boardCode
  val threadNo: Long
    get() = threadDescriptor.threadNo
  val catalogDescriptor: CatalogDescriptor
    get() = threadDescriptor.catalogDescriptor
  val isOP: Boolean
    get() = postNo == threadDescriptor.threadNo

  override fun toString(): String {
    return buildString(capacity = 32) {
      append("PostDescriptor(")
      append(siteKeyActual)
      append("/")
      append(boardCode)
      append("/")
      append(threadNo)
      append("/")
      append(postNo)

      if (postSubNo != null && postSubNo > 0) {
        append("/")
        append(postSubNo)
      }

      append(")")
    }
  }

  companion object {

    fun create(
      siteKey: SiteKey,
      boardCode: String,
      threadNo: Long,
      postNo: Long,
      postSubNo: Long?
    ): PostDescriptor {
      return PostDescriptor(
        threadDescriptor = ThreadDescriptor(
          catalogDescriptor = CatalogDescriptor(
            siteKey = siteKey,
            boardCode = boardCode
          ),
          threadNo = threadNo
        ),
        postNo = postNo,
        postSubNo = postSubNo
      )
    }

  }

}