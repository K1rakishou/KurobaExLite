package com.github.k1rakishou.kurobaexlite.helpers.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LruDiskCacheSynchronizerTest {

  @Test
  fun `should not deadlock when locking with different keys`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = LruDiskCacheSynchronizer<String>()

    cacheHandlerSynchronizer.withLocalLock("1") {
      cacheHandlerSynchronizer.withLocalLock("2") {
        cacheHandlerSynchronizer.withLocalLock("3") {
          cacheHandlerSynchronizer.withLocalLock("4") {
            ++value
          }
        }
      }
    }

    assertEquals(1, value)
    assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test
  fun `should not deadlock when nested locking with the same key`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = LruDiskCacheSynchronizer<String>()

    cacheHandlerSynchronizer.withLocalLock("1") {
      cacheHandlerSynchronizer.withLocalLock("1") {
        ++value
      }
    }

    assertEquals(1, value)
    assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test
  fun `should not deadlock when nested locking with global lock`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = LruDiskCacheSynchronizer<String>()

    cacheHandlerSynchronizer.withGlobalLock {
      cacheHandlerSynchronizer.withGlobalLock {
        ++value
      }
    }

    assertEquals(1, value)
    assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test
  fun `should not deadlock when mixing local and global locks`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = LruDiskCacheSynchronizer<String>()

    cacheHandlerSynchronizer.withGlobalLock {
      cacheHandlerSynchronizer.withLocalLock("1") {
        cacheHandlerSynchronizer.withGlobalLock {
          cacheHandlerSynchronizer.withLocalLock("1") {
            cacheHandlerSynchronizer.withGlobalLock {
              cacheHandlerSynchronizer.withLocalLock("2") {
                cacheHandlerSynchronizer.withLocalLock("3") {
                  ++value
                }
              }
            }
          }
        }
      }
    }

    assertEquals(1, value)
    assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test
  fun `concurrent access from multiple threads only local`() = runTest {
    val values = IntArray(50) { 0 }
    val cacheHandlerSynchronizer = LruDiskCacheSynchronizer<String>()

    (0 until 50).map { id ->
      async(Dispatchers.IO) {
        repeat(100) {
          cacheHandlerSynchronizer.withLocalLock(key = id.toString()) {
            values[id] = values[id] + 1
          }
        }
      }
    }.awaitAll()

    assertEquals(50 * 100, values.sum())
    assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test
  fun `concurrent access from multiple threads only global`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = LruDiskCacheSynchronizer<String>()

    (0 until 50).map { id ->
      async(Dispatchers.IO) {
        repeat(100) {
          cacheHandlerSynchronizer.withGlobalLock {
            ++value
          }
        }
      }
    }.awaitAll()

    assertEquals(50 * 100, value)
    assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test
  fun `concurrent access from multiple threads mixed 1`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = LruDiskCacheSynchronizer<String>()

    (0 until 50).map { id ->
      async(Dispatchers.IO) {
        repeat(100) {
          cacheHandlerSynchronizer.withGlobalLock {
            cacheHandlerSynchronizer.withLocalLock(key = id.toString()) {
              ++value
            }
          }
        }
      }
    }.awaitAll()

    assertEquals(50 * 100, value)
    assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

  @Test
  fun `concurrent access from multiple threads mixed 2`() = runTest {
    var value = 0
    val cacheHandlerSynchronizer = LruDiskCacheSynchronizer<String>()

    (0 until 50).map { id ->
      async(Dispatchers.IO) {
        repeat(100) {
          cacheHandlerSynchronizer.withLocalLock(key = id.toString()) {
            cacheHandlerSynchronizer.withGlobalLock {
              ++value
            }
          }
        }
      }
    }.awaitAll()

    assertEquals(50 * 100, value)
    assertTrue(cacheHandlerSynchronizer.getActiveSynchronizerKeys().isEmpty())
  }

}