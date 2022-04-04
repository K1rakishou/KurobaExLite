package com.github.k1rakishou.kurobaexlite.helpers.cache.disk_lru

import com.github.k1rakishou.kurobaexlite.helpers.removeExtensionFromFileName
import java.io.File
import okhttp3.HttpUrl
import okio.ByteString.Companion.encodeUtf8

class FileId private constructor(val urlSha256: String) {

  init {
    checkHash(urlSha256)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FileId

    if (urlSha256 != other.urlSha256) return false

    return true
  }

  override fun hashCode(): Int {
    return urlSha256.hashCode()
  }

  override fun toString(): String {
    return "FileId(urlSha256='$urlSha256')"
  }

  companion object {
    private const val TAG = "FileId"
    private val allowedCharacters = "1234567890abcdef".toHashSet()

    fun fromUrl(url: HttpUrl): FileId {
      return FileId(url.toString().encodeUtf8().sha256().hex())
    }

    fun fromFile(file: File): FileId {
      return fromFileName(file.name.removeExtensionFromFileName())
    }

    fun fromFileName(fileName: String): FileId {
      val fileId = when {
        fileName.contains("_") -> {
          // "%s_%d_%d.%s"
          // See KurobaInnerLruDiskCache.CHUNK_CACHE_FILE_NAME_FORMAT
          val fileIdHash = fileName.substringBefore('_')
          FileId(fileIdHash)
        }
        fileName.contains(".") -> {
          // "%s.%s"
          // See KurobaInnerLruDiskCache.CACHE_FILE_NAME_FORMAT
          val fileIdHash = fileName.substringBefore('.')
          FileId(fileIdHash)
        }
        else -> {
          checkHash(fileName)
          FileId(fileName)
        }
      }

      return fileId
    }

    private fun checkHash(urlSha256: String) {
      for (ch in urlSha256) {
        if (ch.lowercaseChar() !in allowedCharacters) {
          error("Bad urlSha256: ${urlSha256}, bad character: $ch")
        }
      }
    }
  }

}