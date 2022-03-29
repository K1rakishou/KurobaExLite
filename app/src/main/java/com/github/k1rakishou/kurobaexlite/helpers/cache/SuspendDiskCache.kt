package com.github.k1rakishou.kurobaexlite.helpers.cache

import coil.disk.DiskCache
import com.github.k1rakishou.kurobaexlite.helpers.abortQuietly
import java.util.WeakHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.internal.closeQuietly
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem

class SuspendDiskCache(
  private val diskCache: DiskCache
) {
  private val mutexes = WeakHashMap<String, Mutex>()

  val fileSystem: FileSystem
    get() = diskCache.fileSystem

  suspend fun <T : Any?> withSnapshot(
    key: String,
    func: suspend DiskCache.Snapshot.() -> T
  ): T? {
    return getOrCreateMutex(key).withLock {
      val snapshot = diskCache.get(key)
        ?: return@withLock null

      var success = false

      try {
        val result = func(snapshot)
        success = true

        return@withLock result
      } finally {
        if (!success) {
          snapshot.closeQuietly()
        }
      }
    }
  }

  suspend fun <T : Any?> withEditor(
    key: String,
    func: suspend DiskCache.Editor.() -> T
  ): T {
    return getOrCreateMutex(key).withLock {
      val editor = diskCache.edit(key)!!
      var success = false

      try {
        val result = func(editor)
        success = true

        return@withLock result
      } finally {
        if (!success) {
          editor.abortQuietly()
        }
      }
    }
  }

  private fun String.keyHash(): String = encodeUtf8().sha256().hex()

  @Synchronized
  private fun getOrCreateMutex(key: String): Mutex {
    val keyHash = key.keyHash()

    val mutexFromCache = mutexes[keyHash]
    if (mutexFromCache != null) {
      return mutexFromCache
    }

    val newMutex = Mutex()
    mutexes[keyHash] = newMutex

    return newMutex
  }

}