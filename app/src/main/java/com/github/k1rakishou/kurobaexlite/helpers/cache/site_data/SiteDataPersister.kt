package com.github.k1rakishou.kurobaexlite.helpers.cache.site_data

import android.content.Context
import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.cache.sync.KeySynchronizer
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.squareup.moshi.Moshi
import java.io.File
import okio.buffer
import okio.sink
import okio.source

class SiteDataPersister(
  private val appContext: Context,
  private val moshi: Moshi
) {
  private val synchronizer = KeySynchronizer<PersistableSiteDataKey>()

  private val siteDataDirectory = File(appContext.cacheDir, SITE_DATA_DIRECTORY)
    get() {
      if (!field.exists()) {
        field.mkdirs()
      }

      return field
    }


  suspend inline fun <reified T : PersistableSiteData> read(key: PersistableSiteDataKey): Result<T> {
    return read(key, T::class.java)
  }

  suspend inline fun <reified T : PersistableSiteData> write(key: PersistableSiteDataKey, data: T): Result<Unit> {
    return write(key, data, T::class.java)
  }

  suspend fun <T : PersistableSiteData> read(key: PersistableSiteDataKey, clazz: Class<T>): Result<T> {
    return Result.Try {
      return@Try synchronizer.withLocalLock(key) {
        return@withLocalLock MoshiConverter<T>(moshi, siteDataDirectory)
          .fileToData(key, clazz)
          .unwrap()
      }
    }
  }

  suspend fun <T : PersistableSiteData> write(key: PersistableSiteDataKey, data: T, clazz: Class<T>): Result<Unit> {
    return Result.Try {
      return@Try synchronizer.withLocalLock(key) {
        return@withLocalLock MoshiConverter<T>(moshi, siteDataDirectory)
          .dataToFile(key, data, clazz)
          .unwrap()
      }
    }
  }

  companion object {
    private const val SITE_DATA_DIRECTORY = "site_data"
  }
}

data class PersistableSiteDataKey private constructor(
  val dataPath: String,
  val fileName: String
) {
  companion object {
    fun create(segments: List<String>, fileName: String): PersistableSiteDataKey {
      if (segments.isEmpty()) {
        error("Segments must not be empty")
      }

      return PersistableSiteDataKey(
        dataPath = segments.joinToString(separator = "/"),
        fileName = fileName
      )
    }
  }

}

interface PersistableSiteData

interface Converter<T : PersistableSiteData> {
  fun dataToFile(key: PersistableSiteDataKey, data: T, clazz: Class<T>): Result<Unit>
  fun fileToData(key: PersistableSiteDataKey, clazz: Class<T>): Result<T?>
}

class MoshiConverter<T : PersistableSiteData>(
  private val moshi: Moshi,
  private val siteDataDirectory: File
) : Converter<T> {
  override fun dataToFile(key: PersistableSiteDataKey, data: T, clazz: Class<T>): Result<Unit> {
    return Result.Try {
      val resultDirectory = File(siteDataDirectory, key.dataPath)
      if (!resultDirectory.exists()) {
        resultDirectory.mkdirs()
      }

      val resultFile = File(resultDirectory, key.fileName)

      if (resultFile.exists()) {
        if (!resultFile.delete()) {
          throw DeleteFileException(resultFile.path)
        }
      }

      if (!resultFile.createNewFile()) {
        throw CreateFileException(resultFile.path)
      }

      resultFile.sink().buffer().use { sink ->
        moshi.adapter(clazz).toJson(sink, data)
      }
    }
  }

  override fun fileToData(key: PersistableSiteDataKey, clazz: Class<T>): Result<T> {
    return Result.Try {
      val resultDirectory = File(siteDataDirectory, key.dataPath)
      if (!resultDirectory.exists()) {
        throw DataNotFoundException(key)
      }

      val resultFile = File(resultDirectory, key.fileName)
      if (!resultFile.exists()) {
        throw DataNotFoundException(key)
      }

      val data = resultFile.source().buffer().use { source ->
        moshi.adapter(clazz).fromJson(source)
      }

      if (data == null) {
        throw JsonReadException(key)
      }

      return@Try data
    }
  }
}

fun segments(vararg segments: String): List<String> {
  if (segments.isEmpty()) {
    error("Segments must not be empty")
  }

  return segments.toList()
}

class DataNotFoundException(key: PersistableSiteDataKey) : Exception("Data for key: '$key' not found on disk")
class CreateFileException(filePath: String) : Exception("Failed to create file with path '$filePath'")
class DeleteFileException(filePath: String) : Exception("Failed to delete old file with path '$filePath'")
class JsonReadException(key: PersistableSiteDataKey) : Exception("Failed to convert json to data for key: '$key'")
