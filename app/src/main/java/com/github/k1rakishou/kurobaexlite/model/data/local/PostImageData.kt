package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import okhttp3.HttpUrl

data class PostImageData(
  val thumbnailUrl: HttpUrl,
  val fullImageUrl: HttpUrl,
  override val originalFileNameEscaped: String,
  override val serverFileName: String,
  override val ext: String,
  override val width: Int,
  override val height: Int,
  override val fileSize: Int,
  override val ownerPostDescriptor: PostDescriptor
) : IPostImage {
  override val thumbnailAsUrl: HttpUrl = thumbnailUrl
  override val fullImageAsUrl: HttpUrl = fullImageUrl

  override val thumbnailAsString: String
    get() = thumbnailUrl.toString()
  override val fullImageAsString: String
    get() = fullImageUrl.toString()
}