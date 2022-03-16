package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import okhttp3.HttpUrl

data class PostImageData(
  val thumbnailUrl: HttpUrl,
  val fullImageUrl: HttpUrl,
  val originalFileName: String,
  val serverFileName: String,
  val ext: String,
  val width: Int,
  val height: Int,
  val fileSize: Int,
  val ownerPostDescriptor: PostDescriptor
)