package com.github.k1rakishou.kurobaexlite.helpers

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import logcat.logcat
import java.io.InterruptedIOException
import java.util.*
import javax.net.ssl.SSLException
import kotlin.collections.ArrayList
import kotlin.collections.Collection
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun <T> CancellableContinuation<T>.resumeValueSafe(value: T) {
  if (isActive) {
    resume(value)
  }
}

fun CancellableContinuation<*>.resumeErrorSafe(error: Throwable) {
  if (isActive) {
    resumeWithException(error)
  }
}

inline fun <T> Result.Companion.Try(func: () -> T): Result<T> {
  return try {
    Result.success(func())
  } catch (error: Throwable) {
    Result.failure(error)
  }
}

fun <T> Result<T>.unwrap(): T {
  return getOrThrow()
}

fun Result<*>.exceptionOrThrow(): Throwable {
  if (this.isSuccess) {
    error("Expected Failure but got Success")
  }

  return exceptionOrNull()!!
}

fun safeCapacity(initialCapacity: Int): Int {
  return if (initialCapacity < 16) {
    16
  } else {
    initialCapacity
  }
}

inline fun <T> mutableListWithCap(initialCapacity: Int): MutableList<T> {
  return ArrayList(safeCapacity(initialCapacity))
}

inline fun <T> mutableListWithCap(collection: Collection<*>): MutableList<T> {
  return ArrayList(safeCapacity(collection.size))
}

inline fun <K, V> mutableMapWithCap(initialCapacity: Int): MutableMap<K, V> {
  return HashMap(safeCapacity(initialCapacity))
}

inline fun <K, V> mutableMapWithCap(collection: Collection<*>): MutableMap<K, V> {
  return HashMap(safeCapacity(collection.size))
}

inline fun <K, V> linkedMapWithCap(initialCapacity: Int): LinkedHashMap<K, V> {
  return LinkedHashMap(safeCapacity(initialCapacity))
}

inline fun <K, V> linkedMapWithCap(collection: Collection<*>): LinkedHashMap<K, V> {
  return LinkedHashMap(safeCapacity(collection.size))
}

inline fun <T> mutableSetWithCap(initialCapacity: Int): HashSet<T> {
  return HashSet(safeCapacity(initialCapacity))
}

inline fun <T> mutableSetWithCap(collection: Collection<*>): HashSet<T> {
  return HashSet(safeCapacity(collection.size))
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
inline fun CharSequence?.isNotNullNorEmpty(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorEmpty != null)
  }

  return this != null && this.length > 0
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
inline fun CharSequence?.isNotNullNorBlank(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorBlank != null)
  }

  return this != null && this.isNotBlank()
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
inline fun String?.isNotNullNorEmpty(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorEmpty != null)
  }

  return this != null && this.length > 0
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
inline fun String?.isNotNullNorBlank(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorBlank != null)
  }

  return this != null && this.isNotBlank()
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
inline fun <T> Collection<T>?.isNotNullNorEmpty(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorEmpty != null)
  }

  return this != null && this.size > 0
}

fun Throwable.errorMessageOrClassName(): String {
  if (!isExceptionImportant()) {
    return this::class.java.name
  }

  val actualMessage = if (cause?.message?.isNotNullNorBlank() == true) {
    cause!!.message
  } else {
    message
  }

  if (!actualMessage.isNullOrBlank()) {
    return actualMessage
  }

  return this::class.java.name
}

fun Throwable.isExceptionImportant(): Boolean {
  return when (this) {
    is CancellationException,
    is InterruptedIOException,
    is InterruptedException,
    is SSLException -> false
    else -> true
  }
}

inline fun Any.logcatError(
  tag: String? = null,
  message: () -> String
) {
  logcat(priority = LogPriority.ERROR, tag = tag, message = message)
}