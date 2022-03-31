package com.github.k1rakishou.kurobaexlite.helpers.cache

import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.github.k1rakishou.kurobaexlite.helpers.withReentrantLock
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex

class LruDiskCacheSynchronizer<Key : Any> {

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  @GuardedBy("this")
  val synchronizerMap = WeakHashMap<Key, Synchronizer>()

  private val globalMutex = Mutex()

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  fun getOrCreate(key: Key): Synchronizer {
    var value = synchronizerMap[key]
    if (value == null) {
      synchronized(this) {
        value = synchronizerMap[key]
        if (value == null) {
          value = Synchronizer()
          synchronizerMap[key] = value
        }
      }
    }

    return value!!
  }

  /**
   * Get keys of all synchronizers that are currently active (Synchronizer.counter > 0)
   * */
  @Synchronized
  fun getActiveSynchronizerKeys(): Set<Key> {
    return synchronizerMap.entries
      .filter { (_, synchronizer) -> synchronizer.counter > 0 }
      .map { (key, _) -> key }.toSet()
  }

  suspend fun <T : Any?> withLocalLock(key: Key, func: suspend () -> T): T {
    if (globalMutex.isLocked) {
      return globalMutex.withReentrantLock { getOrCreate(key).withLock { func() } }
    }

    return getOrCreate(key).withLock { func() }
  }

  suspend fun <T : Any?> withGlobalLock(func: suspend () -> T): T {
    return globalMutex.withReentrantLock { func() }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  class Synchronizer(
    val mutex: Mutex = Mutex(),
    private val _counter: AtomicLong = AtomicLong(0)
  ) {
    val counter: Long
      get() = _counter.get()

    suspend fun <T> withLock(block: suspend () -> T): T {
      try {
        _counter.incrementAndGet()
        return mutex.withReentrantLock(block)
      } finally {
        _counter.decrementAndGet()
      }
    }
  }

}