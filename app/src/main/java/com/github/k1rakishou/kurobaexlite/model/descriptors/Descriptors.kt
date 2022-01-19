package com.github.k1rakishou.kurobaexlite.model.descriptors

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

inline class SiteKey(val key: String)

sealed class ChanDescriptor

@Parcelize
data class CatalogDescriptor(
  val siteKey: SiteKey,
  val boardCode: String
) : Parcelable, ChanDescriptor()

@Parcelize
data class ThreadDescriptor(
  val catalogDescriptor: CatalogDescriptor,
  val threadNo: Long
) : Parcelable, ChanDescriptor()

@Parcelize
data class PostDescriptor(
  val threadDescriptor: ThreadDescriptor,
  val postNo: Long,
  val postSubNo: Long? = null
) : Parcelable {
  val catalogDescriptor: CatalogDescriptor
    get() = threadDescriptor.catalogDescriptor

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