package com.github.k1rakishou.kurobaexlite.model.data

import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.*

@Immutable
interface IPostImage {
  val originalFileNameEscaped: String
  val serverFileName: String
  val ext: String
  val width: Int
  val height: Int
  val fileSize: Long
  val thumbnailSpoiler: ThumbnailSpoiler?

  val ownerPostDescriptor: PostDescriptor

  val thumbnailAsUrl: HttpUrl
  val fullImageAsUrl: HttpUrl

  val thumbnailAsString: String
  val fullImageAsString: String

  fun uniqueKey(): String {
    return "${ownerPostDescriptor.asKey()}_${fullImageAsUrl}"
  }

}

@Immutable
sealed interface ThumbnailSpoiler {
  fun spoilerThumbnailUrl(chanDescriptor: ChanDescriptor): HttpUrl?

  data class Chan4(val spoilerId: Int) : ThumbnailSpoiler {
    override fun spoilerThumbnailUrl(chanDescriptor: ChanDescriptor): HttpUrl {
      return "https://s.4cdn.org/image/spoiler-${chanDescriptor.boardCode}${spoilerId}.png".toHttpUrl()
    }
  }

}

fun IPostImage.originalFileNameForPostCell(maxLength: Int = 80): String {
  val cutMarker = "[...]"

  if (originalFileNameEscaped.length <= (maxLength + cutMarker.length)) {
    return originalFileNameEscaped
  }

  return buildString {
    append(originalFileNameEscaped.take(maxLength / 2))
    append(cutMarker)
    append(originalFileNameEscaped.takeLast(maxLength / 2))
  }
}

fun IPostImage.originalFileNameWithExtension(): String {
  return "${originalFileNameEscaped}.${ext}"
}

fun IPostImage.imageNameForDiskStore(duplicateIndex: Int? = null): String {
  val duplicateIndexString = duplicateIndex?.let { index -> "_(${index})" } ?: ""

  if (originalFileNameEscaped.isNotNullNorBlank()) {
    return "${originalFileNameEscaped}${duplicateIndexString}.${ext}"
  }

  return "${serverFileName}${duplicateIndexString}.${ext}"
}

fun IPostImage.aspectRatio(): Float {
  return width.toFloat() / height.toFloat()
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

fun IPostImage.mimeType(): String {
  val extension = ext.lowercase(Locale.ENGLISH)
  return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
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