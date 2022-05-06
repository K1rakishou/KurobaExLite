package com.github.k1rakishou.kurobaexlite.helpers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private object UNINITIALIZED_VALUE

class LazySuspend<out T>(
  initializer: suspend () -> T
) {
  private val mutex = Mutex()
  private var initializer: (suspend () -> T)? = initializer
  @Volatile private var _value: Any? = UNINITIALIZED_VALUE

  fun isInitialized(): Boolean = _value !== UNINITIALIZED_VALUE

  suspend fun value(): T {
    val _v1 = _value
    if (_v1 !== UNINITIALIZED_VALUE) {
      @Suppress("UNCHECKED_CAST")
      return _v1 as T
    }

    return mutex.withLock {
      val _v2 = _value
      if (_v2 !== UNINITIALIZED_VALUE) {
        return@withLock @Suppress("UNCHECKED_CAST") (_v2 as T)
      } else {
        val typedValue = initializer!!()
        _value = typedValue
        initializer = null
        return@withLock typedValue
      }
    }
  }

  override fun toString(): String {
    if (_value != null) {
      return _value.toString()
    } else {
      return valueNotInitializedMsg
    }
  }

  companion object {
    private const val valueNotInitializedMsg = "Lazy value not initialized yet."
  }

}