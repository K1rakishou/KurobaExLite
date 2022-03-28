package com.github.k1rakishou.kurobaexlite.model.data

import androidx.compose.runtime.Stable
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import okhttp3.HttpUrl

@Stable
interface IPostImage {
  val originalFileName: String
  val serverFileName: String
  val ext: String
  val width: Int
  val height: Int
  val fileSize: Int
  val ownerPostDescriptor: PostDescriptor

  val thumbnailAsUrl: HttpUrl
  val fullImageAsUrl: HttpUrl

  val thumbnailAsString: String
  val fullImageAsString: String
}