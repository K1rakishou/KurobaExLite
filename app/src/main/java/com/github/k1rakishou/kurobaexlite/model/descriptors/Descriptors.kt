package com.github.k1rakishou.kurobaexlite.model.descriptors

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

inline class SiteKey(val key: String)

@Parcelize
data class CatalogDescriptor(
  val siteKey: SiteKey,
  val boardCode: String
) : Parcelable

@Parcelize
data class ThreadDescriptor(
  val catalogDescriptor: CatalogDescriptor,
  val threadNo: Long
) : Parcelable

@Parcelize
data class PostDescriptor(
  val threadDescriptor: ThreadDescriptor,
  val postNo: Long,
  val postSubNo: Long? = null
) : Parcelable {

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