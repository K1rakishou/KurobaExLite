package com.github.k1rakishou.kurobaexlite.helpers

import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import logcat.logcat
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import java.io.InterruptedIOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException
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

fun lerpFloat(from: Float, to: Float, progress: Float): Float {
  return from + progress * (to - from)
}


inline fun <T, R> List<T>.bidirectionalMap(
  startPosition: Int = size / 2,
  crossinline mapper: (T) -> R
): List<R> {
  return this.bidirectionalSequence(startPosition)
    .map { element -> mapper(element) }
    .toList()
}

fun <T> List<T>.bidirectionalSequenceIndexed(startPosition: Int = size / 2): Sequence<IndexedValue<T>> {
  return sequence<IndexedValue<T>> {
    if (isEmpty()) {
      return@sequence
    }

    if (size == 1) {
      yield(IndexedValue(index = 0, value = first()))
      return@sequence
    }

    var position = startPosition
    var index = 0
    var increment = true

    var reachedLeftSide = false
    var reachedRightSide = false

    while (true) {
      val element = getOrNull(position)
      if (element == null) {
        if (reachedLeftSide && reachedRightSide) {
          break
        }

        if (position <= 0) {
          reachedLeftSide = true
        }

        if (position >= lastIndex) {
          reachedRightSide = true
        }
      }

      if (element != null) {
        yield(IndexedValue(index = position, value = element))
      }

      ++index

      if (increment) {
        position += index
      } else {
        position -= index
      }

      increment = increment.not()
    }
  }
}

fun <T> List<T>.bidirectionalSequence(startPosition: Int = size / 2): Sequence<T> {
  return sequence<T> {
    if (isEmpty()) {
      return@sequence
    }

    if (size == 1) {
      yield(first())
      return@sequence
    }

    var position = startPosition
    var index = 0
    var increment = true

    var reachedLeftSide = false
    var reachedRightSide = false

    while (true) {
      val element = getOrNull(position)
      if (element == null) {
        if (reachedLeftSide && reachedRightSide) {
          break
        }

        if (position <= 0) {
          reachedLeftSide = true
        }

        if (position >= lastIndex) {
          reachedRightSide = true
        }
      }

      if (element != null) {
        yield(element)
      }

      ++index

      if (increment) {
        position += index
      } else {
        position -= index
      }

      increment = increment.not()
    }
  }
}

fun decodeUrlOrNull(
  input: String,
  encoding: String = StandardCharsets.UTF_8.name()
): String? {
  return try {
    URLDecoder.decode(input, encoding)
  } catch (error: Throwable) {
    null
  }
}

inline fun buildAnnotatedString(
  capacity: Int,
  builder: (AnnotatedString.Builder).() -> Unit
): AnnotatedString {
  return AnnotatedString.Builder(capacity = capacity.coerceAtLeast(16))
    .apply(builder)
    .toAnnotatedString()
}

inline fun <T, K> Iterable<T>.toHashSetByKey(capacity: Int = 16, keySelector: (T) -> K): java.util.HashSet<T> {
  val keyDuplicateSet = mutableSetWithCap<K>(capacity)
  val resultHashSet = mutableSetWithCap<T>(capacity)

  for (element in this) {
    if (keyDuplicateSet.add(keySelector(element))) {
      resultHashSet.add(element)
    }
  }

  return resultHashSet
}

inline fun <T> MutableCollection<T>.removeIfKt(filter: (T) -> Boolean): Boolean {
  var removed = false
  val mutableIterator = iterator()

  while (mutableIterator.hasNext()) {
    if (filter.invoke(mutableIterator.next())) {
      mutableIterator.remove()
      removed = true
    }
  }

  return removed
}

inline fun <E> MutableCollection<E>.mutableIteration(func: (MutableIterator<E>, E) -> Boolean) {
  val iterator = this.iterator()

  while (iterator.hasNext()) {
    if (!func(iterator, iterator.next())) {
      return
    }
  }
}

inline fun <K, V> MutableMap<K, V>.mutableIteration(func: (MutableIterator<Map.Entry<K, V>>, Map.Entry<K, V>) -> Boolean) {
  val iterator = this.iterator()

  while (iterator.hasNext()) {
    if (!func(iterator, iterator.next())) {
      return
    }
  }
}

@JvmOverloads
fun Thread.callStack(tag: String = ""): String {
  val resultString = java.lang.StringBuilder(256)
  var index = 0

  for (ste in Thread.currentThread().stackTrace) {
    val className = ste?.className ?: continue
    val fileName = ste.fileName ?: continue
    val methodName = ste.methodName ?: continue
    val lineNumber = ste.lineNumber

    if (!className.startsWith("com.github.k1rakishou")) {
      continue
    }

    if (fileName.contains("GenericExtensions.kt") && methodName.contains("callStack")) {
      continue
    }

    if (index > 0) {
      resultString.appendLine()
    }

    if (tag.isNotEmpty()) {
      resultString.append(tag)
    }

    resultString.append("${index}-[${fileName}:${lineNumber}]")
    resultString.append(" ")
    resultString.append(className)
    resultString.append("#")
    resultString.append(methodName)

    ++index
  }

  return resultString.toString()
}

fun Buffer.writeUtfString(string: String) {
  writeLong(string.length.toLong())
  writeString(string, StandardCharsets.UTF_8)
}

fun Buffer.readUtfString(): String {
  val length = readLong()
  return readString(length, StandardCharsets.UTF_8)
}

fun AnnotatedString.Range<String>.extractLinkableAnnotationItem(): PostCommentParser.TextPartSpan.Linkable? {
  val base64Decoded = item.decodeBase64()
    ?: return null

  val buffer = Buffer()
  buffer.write(base64Decoded)

  return PostCommentParser.TextPartSpan.Linkable.deserialize(buffer)
}

fun PostCommentParser.TextPartSpan.Linkable.createAnnotationItem(): String {
  return serialize().readByteString().base64()
}

fun <T> CancellableContinuation<T>.resumeSafe(value: T) {
  if (isActive) {
    resume(value)
  }
}

fun CancellableContinuation<*>.resumeSafe(error: Throwable) {
  if (isActive) {
    resumeWithException(error)
  }
}
