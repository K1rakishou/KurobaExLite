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
  val siteKey: String
    get() = catalogDescriptor.siteKey.key
  val boardCode: String
    get() = catalogDescriptor.boardCode

  override fun toString(): String {
    return buildString(capacity = 32) {
      append("ThreadDescriptor(")
      append(siteKey)
      append("/")
      append(boardCode)
      append("/")
      append(threadNo)
      append(")")
    }
  }

}

@Parcelize
data class PostDescriptor(
  val threadDescriptor: ThreadDescriptor,
  val postNo: Long,
  val postSubNo: Long? = null
) : Parcelable {
  val siteKey: String
    get() = threadDescriptor.catalogDescriptor.siteKey.key
  val boardCode: String
    get() = threadDescriptor.catalogDescriptor.boardCode
  val threadNo: Long
    get() = threadDescriptor.threadNo
  val catalogDescriptor: CatalogDescriptor
    get() = threadDescriptor.catalogDescriptor

  override fun toString(): String {
    return buildString(capacity = 32) {
      append("PostDescriptor(")
      append(siteKey)
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