package com.github.k1rakishou.kurobaexlite.model.data

import androidx.compose.runtime.Stable
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import java.util.Locale
import okhttp3.HttpUrl

@Stable
interface IPostImage {
  val originalFileNameEscaped: String
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

fun IPostImage.imageType(): ImageType {
  val extension = ext.lowercase(Locale.ENGLISH)

  if (
    extension == "webm" ||
    extension == "mp4" ||
    extension == "mp3" ||
    extension == "m4a" ||
    extension == "ogg" ||
    extension == "flac" ||
    extension == "gif"
  ) {
    return ImageType.Video
  }

  if (
    extension == "jpg" ||
    extension == "jpeg" ||
    extension == "png" ||
    extension == "webp"
  ) {
    return ImageType.Static
  }

  return ImageType.Unsupported
}

enum class ImageType(val value: Int) {
  Static(0),
  Video(1),
  Unsupported(999);

  companion object {
    fun fromValue(value: Int): ImageType {
      return values().first { it.value == value }
    }
  }
}