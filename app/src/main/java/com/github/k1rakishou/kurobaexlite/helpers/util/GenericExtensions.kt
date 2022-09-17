package com.github.k1rakishou.kurobaexlite.helpers.util

import android.app.Activity
import android.os.Binder
import android.os.Parcelable
import android.util.Size
import android.util.SizeF
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPartSpan
import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.BypassException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.github.k1rakishou.kurobaexlite.model.FirewallDetectedException
import com.github.k1rakishou.kurobaexlite.ui.helpers.LocalComponentActivity
import java.io.IOException
import java.io.InterruptedIOException
import java.io.Serializable
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import javax.net.ssl.SSLException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import okhttp3.HttpUrl
import okhttp3.Request
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.context.GlobalContext


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

fun <T> Result<T>.mapErrorToError(mapper: (Throwable) -> Throwable): Result<T> {
  if (isFailure) {
    return Result.failure(mapper(exceptionOrThrow()))
  }

  return this
}

fun <T> Result<T>.ignore() {
  return
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

fun <T> List<T>.ensureSingleMeasurableReturned(): T {
  if (size != 1) {
    error(
      "Expected subcompose() to have only return a single measurable but got ${size} instead. " +
        "Most likely you are trying to emit multiple composables inside of the content() lambda. " +
        "Wrap those composables into any container (Box/Column/Row/etc.) and this crash should go away."
    )
  }

  return first()
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

inline fun <T, R> Iterable<T>.flatMapNotNull(transform: (T) -> Iterable<R>?): List<R> {
  return flatMapNotNullTo(java.util.ArrayList<R>(), transform)
}

inline fun <T, R, C : MutableCollection<in R>> Iterable<T>.flatMapNotNullTo(
  destination: C,
  transform: (T) -> Iterable<R>?
): C {
  this
    .mapNotNull { transform(it) }
    .forEach { destination.addAll(it) }
  return destination
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

@JvmOverloads
fun Throwable.errorMessageOrClassName(userReadable: Boolean = false): String {
  if (!isExceptionImportant()) {
    return this::class.java.name
  }

  val actualMessage = if (cause?.message?.isNotNullNorBlank() == true) {
    cause!!.message
  } else {
    message
  }

  if (userReadable && actualMessage.isNotNullNorBlank()) {
    return actualMessage
  }

  val exceptionClassName = this::class.java.name

  if (!actualMessage.isNullOrBlank()) {
    if (actualMessage.contains(exceptionClassName)) {
      return actualMessage
    }

    return "${exceptionClassName}: ${actualMessage}"
  }

  return exceptionClassName
}

fun Throwable.asLogIfImportantOrErrorMessage(): String {
  if (isExceptionImportant()) {
    return asLog()
  }

  return errorMessageOrClassName()
}

fun Throwable.isExceptionImportant(): Boolean {
  return when (this) {
    is CancellationException,
    is SocketTimeoutException,
    is TimeoutException,
    is InterruptedIOException,
    is InterruptedException,
    is FirewallDetectedException,
    is BadStatusResponseException,
    is EmptyBodyResponseException,
    is BypassException,
    is SSLException -> false
    else -> true
  }
}

inline fun logcatError(
  tag: String,
  message: () -> String
) {
  logcat(priority = LogPriority.ERROR, tag = tag, message = message)
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

inline fun <E> Collection<E>.iteration(func: (Iterator<E>, E) -> Boolean) {
  val iterator = this.iterator()

  while (iterator.hasNext()) {
    if (!func(iterator, iterator.next())) {
      return
    }
  }
}

inline fun <E> MutableCollection<E>.mutableIteration(func: (MutableIterator<E>, E) -> Boolean) {
  val iterator = this.iterator()

  while (iterator.hasNext()) {
    if (!func(iterator, iterator.next())) {
      return
    }
  }
}

inline fun <E> MutableCollection<E>.mutableIndexedIteration(func: (Int, MutableIterator<E>, E) -> Boolean) {
  val iterator = this.iterator()
  var index = 0

  while (iterator.hasNext()) {
    if (!func(index, iterator, iterator.next())) {
      return
    }

    ++index
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

fun <T> MutableList<T>.move(fromIdx: Int, toIdx: Int): Boolean {
  if (fromIdx == toIdx) {
    return false
  }

  if (fromIdx < 0 || fromIdx >= size) {
    return false
  }

  if (toIdx < 0 || toIdx >= size) {
    return false
  }

  if (toIdx > fromIdx) {
    for (i in fromIdx until toIdx) {
      this[i] = this[i + 1].also { this[i + 1] = this[i] }
    }
  } else {
    for (i in fromIdx downTo toIdx + 1) {
      this[i] = this[i - 1].also { this[i - 1] = this[i] }
    }
  }

  return true
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

fun AnnotatedString.Range<String>.extractLinkableAnnotationItem(): TextPartSpan.Linkable? {
  if (item.isEmpty()) {
    return null
  }

  val base64Decoded = item.decodeBase64()
    ?: return null

  val buffer = Buffer()
  buffer.write(base64Decoded)

  return TextPartSpan.Linkable.deserialize(buffer)
}

fun TextPartSpan.Linkable.createAnnotationItem(): String {
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

fun <T> maxOfByOrNull(vararg elements: T, selector: (T) -> Number): T? {
  if (elements.isEmpty()) {
    return null
  }

  var maxElem = elements.first()
  var maxValue = selector(maxElem).toLong()

  for (index in 1 until elements.size) {
    val element = elements[index]
    val elementValue = selector(element).toLong()

    if (elementValue > maxValue) {
      maxValue = elementValue
      maxElem = element
    }
  }

  return maxElem
}

fun Number.asReadableFileSize(): String {
  val bytes = toLong()

  // Nice stack overflow copy-paste, but it's been updated to be more correct
  // https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
  val s = if (bytes < 0) {
    "-"
  } else {
    ""
  }

  var b = if (bytes == Long.MIN_VALUE) {
    Long.MAX_VALUE
  } else {
    abs(bytes)
  }

  return when {
    b < 1000L -> "$bytes B"
    b < 999950L -> String.format("%s%.1f kB", s, b / 1e3)
    1000.let { b /= it; b } < 999950L -> String.format("%s%.1f MB", s, b / 1e3)
    1000.let { b /= it; b } < 999950L -> String.format("%s%.1f GB", s, b / 1e3)
    1000.let { b /= it; b } < 999950L -> String.format("%s%.1f TB", s, b / 1e3)
    1000.let { b /= it; b } < 999950L -> String.format("%s%.1f PB", s, b / 1e3)
    else -> String.format("%s%.1f EB", s, b / 1e6)
  }
}

suspend fun <T> Mutex.withLockNonCancellable(owner: Any? = null, action: suspend () -> T): T {
  return withContext(NonCancellable) { withLock(owner) { action.invoke() } }
}

fun unreachable(message: String? = null): Nothing = error(message ?: "Unreachable!")

fun String.removeExtensionFromFileName(): String {
  val index = this.lastIndexOf('.')
  if (index == -1) {
    return this
  }

  return this.substring(0, index)
}

fun View.emulateMotionEvent(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
  val motionEvent = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
  (context as Activity).dispatchTouchEvent(motionEvent)
  motionEvent.recycle()
}

@Composable
inline fun <reified T : Any> koinRemember(): T {
  return remember { GlobalContext.get().get<T>() }
}

@Composable
inline fun <reified T : ViewModel> koinRememberViewModel(): T {
  val componentActivity = LocalComponentActivity.current
  return remember { componentActivity.koinViewModel<T>() }
}

inline fun <reified T : ViewModel> ComponentActivity.koinViewModel(): T {
  return this.viewModel<T>().value
}

private const val COOKIE_HEADER_NAME = "Cookie"

fun Request.Builder.appendCookieHeader(value: String): Request.Builder {
  val request = build()

  val cookies = request.header(COOKIE_HEADER_NAME)
  if (cookies == null) {
    return addHeader(COOKIE_HEADER_NAME, value)
  }

  // Absolute retardiation but OkHttp doesn't allow doing it differently (or maybe I just don't know?)
  val fullCookieValue = request.newBuilder()
    .removeHeader(COOKIE_HEADER_NAME)
    .addHeader(COOKIE_HEADER_NAME, "${cookies}; ${value}")
    .build()
    .header(COOKIE_HEADER_NAME)!!

  return header(COOKIE_HEADER_NAME, fullCookieValue)
}

fun String?.asFormattedToken(): String {
  if (this == null) {
    return "<null>"
  }

  if (this.isEmpty()) {
    return "<empty>"
  }

  val tokenPartLength = (this.length.toFloat() * 0.2f).toInt() / 2
  val startTokenPart = this.substring(0, tokenPartLength)
  val endTokenPart = this.substring(this.length - tokenPartLength)

  return "${startTokenPart}<cut>${endTokenPart}"
}

fun Matcher.groupOrNull(group: Int): String? {
  return try {
    if (group < 0 || group > groupCount()) {
      return null
    }

    this.group(group)
  } catch (error: Throwable) {
    null
  }
}

suspend fun <T, R> processDataCollectionConcurrently(
  dataList: Collection<T>,
  batchCount: Int,
  dispatcher: CoroutineDispatcher,
  processFunc: suspend (T) -> R?
): List<R> {
  if (dataList.isEmpty()) {
    return emptyList()
  }

  return supervisorScope {
    return@supervisorScope dataList
      .chunked(batchCount)
      .flatMap { dataChunk ->
        return@flatMap dataChunk
          .map { data ->
            return@map async(dispatcher) {
              try {
                ensureActive()
                return@async processFunc(data)
              } catch (error: Throwable) {
                return@async null
              }
            }
          }
          .awaitAll()
          .filterNotNull()
      }
  }
}

@Suppress("UnnecessaryVariable")
suspend fun <T : Any> retryableIoTask(attempts: Int, task: suspend (Int) -> T): T {
  require(attempts > 0) { "Bad attempts count: $attempts" }
  val retries = AtomicInteger(0)

  return coroutineScope {
    while (true) {
      ensureActive()

      try {
        // Try to execute a task
        val result = task(retries.incrementAndGet())

        // If no exceptions were thrown then just exit
        return@coroutineScope result
      } catch (error: IOException) {
        // If any kind of IOException was thrown then retry until we either succeed or exhaust all
        // attempts
        if (retries.get() >= attempts) {
          throw error
        }
      }
    }

    throw RuntimeException("Shouldn't be called")
  }
}

private const val maxExtensionLength = 8

fun String.removeExtensionIfPresent(): String {
  val index = this.lastIndexOf('.')
  if (index < 0) {
    return this
  }

  return this.substring(0, index)
}

fun String.extractFileNameExtension(): String? {
  val index = this.lastIndexOf('.')
  if (index == -1) {
    return null
  }

  val indexOfFirstBadCharacter = indexOfFirst(
    startIndex = index + 1,
    endIndex = (index + 1) + maxExtensionLength,
    predicate = { ch -> !ch.isLetterOrDigit() }
  )

  if (indexOfFirstBadCharacter != null) {
    return this.substring(index + 1, indexOfFirstBadCharacter)
  }

  return this.substring(index + 1).takeIf { extension -> extension.length <= maxExtensionLength }
}

fun String.indexOfFirst(
  startIndex: Int = 0,
  endIndex: Int = lastIndex,
  predicate: (Char) -> Boolean
): Int? {
  for (i in startIndex until endIndex) {
    val ch = this.getOrNull(i)
      ?: break

    if (predicate(ch)) {
      return i
    }
  }

  return null
}

fun String.findAllOccurrences(query: String?, minQueryLength: Int): List<IntRange> {
  if (this.isEmpty() || (query == null || query.length < minQueryLength)) {
    return emptyList()
  }

  check(query.isNotEmpty()) { "query must not be empty" }

  val resultList = mutableListWithCap<IntRange>(16)
  var index = 0

  while (index < this.length) {
    val ch = this.getOrNull(index)
      ?: break

    if (ch.equals(other = query[0], ignoreCase = true)) {
      var found = true

      for (queryOffset in 1 until query.length) {
        val innerCh = this.getOrNull(index + queryOffset)

        if (innerCh == null || !innerCh.equals(other = query[queryOffset], ignoreCase = true)) {
          found = false
          index += queryOffset
          break
        }
      }

      if (!found) {
        continue
      }

      resultList += IntRange(index, index + query.length)
      index += query.length
    }

    ++index
  }

  return resultList
}

fun Float.quantize(precision: Float): Float {
  val additionalPrecision = if (this >= -1f && this <= 1f) {
    if (this >= 0f) {
      precision * 1f
    } else {
      precision * -1f
    }
  } else {
    0f
  }

  return if (this >= 0f) {
    (floor((this.toDouble() / precision.toDouble()) + additionalPrecision) * precision).toFloat()
  } else {
    (ceil((this.toDouble() / precision.toDouble()) + additionalPrecision) * precision).toFloat()
  }
}

fun <T> MutableList<T>.moveToEnd(index: Int) {
  if (getOrNull(index) == null) {
    return
  }

  this.add(this.removeAt(index))
}

fun rawSizeToLong(size: String): Long {
  return when (size.uppercase(Locale.ENGLISH)) {
    "KB" -> 1000
    "KIB" -> 1024
    "MB" -> 1000 * 1000
    "MIB" -> 1024 * 1024
    "GB" -> 1000 * 1000 * 1000
    "GIB" -> 1024 * 1024 * 1024
    "B" -> 1
    else -> 1024
  }
}

private const val HTTP = "http://"
private const val HTTPS = "https://"

fun fixUrlOrNull(inputUrlRaw: String?): String? {
  if (inputUrlRaw == null) {
    return null
  }

  if (inputUrlRaw.startsWith("//")) {
    return HTTPS + inputUrlRaw.removePrefix("//")
  }

  if (inputUrlRaw.startsWith(HTTPS)) {
    return inputUrlRaw
  }

  if (inputUrlRaw.startsWith(HTTP)) {
    return HTTPS + inputUrlRaw.removePrefix(HTTP)
  }

  return HTTPS + inputUrlRaw
}

fun HttpUrl.extractFileName(): String? {
  return this.pathSegments.lastOrNull()?.substringAfterLast("/")
}

fun HttpUrl.domain(): String? {
  val host = host.removePrefix("www.")
  if (host.isEmpty()) {
    return null
  }

  var topDomainSeparatorFound = false
  var indexOfDomainSeparator = -1

  for (index in host.lastIndex downTo 0) {
    if (host[index] == '.') {
      if (!topDomainSeparatorFound) {
        topDomainSeparatorFound = true
        continue
      }

      indexOfDomainSeparator = index
      break
    }
  }

  if (indexOfDomainSeparator < 0) {
    return host
  }

  return host.substring(indexOfDomainSeparator + 1, host.length)
}

private val AcceptableClasses = arrayOf(
  Serializable::class.java,
  Parcelable::class.java,
  String::class.java,
  SparseArray::class.java,
  Binder::class.java,
  Size::class.java,
  SizeF::class.java
)

fun checkCanUseType(value: Any): Boolean {
  for (cl in AcceptableClasses) {
    if (cl.isInstance(value)) {
      return true
    }
  }

  return false
}

fun ByteArray.containsPattern(startFrom: Int, pattern: ByteArray): Boolean {
  if (pattern.size > this.size) {
    return false
  }

  for (offset in startFrom until this.size) {
    if (pattern[0] == this[offset]) {
      if (checkPattern(this, offset, pattern)) {
        return true
      }
    }
  }

  return false
}

private fun checkPattern(input: ByteArray, offset: Int, pattern: ByteArray): Boolean {
  for (index in pattern.indices) {
    if (pattern[index] != input[offset + index]) {
      return false
    }
  }

  return true
}

suspend fun <T> CompletableDeferred<T>.awaitSilently(defaultValue: T): T {
  return try {
    await()
  } catch (ignored: CancellationException) {
    defaultValue
  }
}

suspend fun CompletableDeferred<*>.awaitSilently() {
  try {
    await()
  } catch (ignored: CancellationException) {
    // no-op
  }
}
