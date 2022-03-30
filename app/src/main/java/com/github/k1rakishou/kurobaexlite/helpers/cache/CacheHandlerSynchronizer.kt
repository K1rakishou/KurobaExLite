package com.github.k1rakishou.kurobaexlite.helpers.cache

import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.github.k1rakishou.kurobaexlite.helpers.withReentrantLock
import java.util.WeakHashMap
import kotlinx.coroutines.sync.Mutex

/**
 * A synchronizer class for CacheHandler that allows synchronization on a value of the key.
 * This is very useful for CacheHandler since we want to lock disk access per file separately not
 * for the whole disk at a time. This should drastically improve CacheHandler's performance when
 * many different threads access different files. In the previous implementation we would lock
 * access to disk globally every time a thread is doing something with a file which could slow down
 * everything when there were a lot of disk access from multiple threads
 * (Album with 5 columns + prefetch + high-res thumbnails + huge cache size (1GB+).)
 * */
class CacheHandlerSynchronizer<Key : Any> {
  @GuardedBy("this")
  private val synchronizerMap = WeakHashMap<Key, Mutex>()

  private val globalMutex = Mutex()

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  fun getOrCreate(key: Key): Mutex {
    var value = synchronizerMap[key]
    if (value == null) {
      synchronized(this) {
        value = synchronizerMap[key]
        if (value == null) {
          value = Mutex()
          synchronizerMap[key] = value
        }
      }
    }

    return value!!
  }

  suspend fun <T : Any?> withLocalLock(key: Key, func: suspend () -> T): T {
    if (globalMutex.isLocked) {
      return globalMutex.withReentrantLock { getOrCreate(key).withReentrantLock { func() } }
    }

    return getOrCreate(key).withReentrantLock { func() }
  }

  suspend fun <T : Any?> withGlobalLock(func: suspend () -> T): T {
    return globalMutex.withReentrantLock { func()}
  }

}