package com.github.k1rakishou.kurobaexlite.model.data.ui.post

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.model.data.IPostImage
import com.github.k1rakishou.kurobaexlite.model.data.ThumbnailSpoiler
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Immutable
data class PostCellImageData(
  val thumbnailUrl: String,
  val fullImageUrl: String,
  override val originalFileNameEscaped: String,
  override val serverFileName: String,
  override val ext: String,
  override val width: Int,
  override val height: Int,
  override val fileSize: Int,
  override val thumbnailSpoiler: ThumbnailSpoiler?,
  override val ownerPostDescriptor: PostDescriptor
) : IPostImage {

  override val thumbnailAsUrl: HttpUrl by lazy { thumbnailUrl.toHttpUrl() }
  override val fullImageAsUrl: HttpUrl by lazy { fullImageUrl.toHttpUrl() }

  override val thumbnailAsString: String
    get() = thumbnailUrl
  override val fullImageAsString: String
    get() = fullImageUrl

  companion object {
    fun fromPostImageData(postImageData: IPostImage): PostCellImageData {
      return PostCellImageData(
        thumbnailUrl = postImageData.thumbnailAsString,
        fullImageUrl = postImageData.fullImageAsString,
        originalFileNameEscaped = postImageData.originalFileNameEscaped,
        serverFileName = postImageData.serverFileName,
        ext = postImageData.ext,
        width = postImageData.width,
        height = postImageData.height,
        fileSize = postImageData.fileSize,
        thumbnailSpoiler = postImageData.thumbnailSpoiler,
        ownerPostDescriptor = postImageData.ownerPostDescriptor,
      )
    }
  }
}