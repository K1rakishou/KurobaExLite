package com.github.k1rakishou.kpnc.helpers

import com.github.k1rakishou.kpnc.model.BadStatusResponseException
import com.github.k1rakishou.kpnc.model.EmptyBodyResponseException
import com.github.k1rakishou.kpnc.model.JsonConversionException
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.*
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import okhttp3.*
import okio.*
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.resume
import kotlin.io.use

internal inline fun logcatError(
  tag: String,
  message: () -> String
) {
  logcat(priority = LogPriority.ERROR, tag = tag, message = message)
}

internal inline fun Any.logcatError(
  tag: String? = null,
  message: () -> String
) {
  logcat(priority = LogPriority.ERROR, tag = tag, message = message)
}

internal inline fun logcatVerbose(
  tag: String,
  message: () -> String
) {
  logcat(priority = LogPriority.VERBOSE, tag = tag, message = message)
}

internal inline fun Any.logcatVerbose(
  tag: String? = null,
  message: () -> String
) {
  logcat(priority = LogPriority.VERBOSE, tag = tag, message = message)
}

internal inline fun logcatDebug(
  tag: String,
  message: () -> String
) {
  logcat(priority = LogPriority.DEBUG, tag = tag, message = message)
}

internal inline fun Any.logcatDebug(
  tag: String? = null,
  message: () -> String
) {
  logcat(priority = LogPriority.DEBUG, tag = tag, message = message)
}

internal suspend fun OkHttpClient.suspendCall(request: Request): Result<Response> {
  val responseResult = suspendCancellableCoroutine<Result<Response>> { continuation ->
    val call = newCall(request)

    continuation.invokeOnCancellation {
      if (!call.isCanceled()) {
        call.cancel()
      }
    }

    call.enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        continuation.resumeValueSafe(Result.failure(e))
      }

      override fun onResponse(call: Call, response: Response) {
        continuation.resumeValueSafe(Result.success(response))
      }
    })
  }

  val response = if (responseResult.isFailure) {
    return responseResult
  } else {
    responseResult.getOrThrow()
  }

  if (!response.isSuccessful) {
    return Result.failure(BadStatusResponseException(response.code))
  }

  if (response.body == null) {
    return Result.failure(EmptyBodyResponseException())
  }

  return Result.success(response)
}

internal suspend inline fun <reified T : Any?> OkHttpClient.suspendConvertWithJsonAdapter(
  request: Request,
  adapter: JsonAdapter<T>
): Result<out T> {
  return withContext(Dispatchers.IO) {
    return@withContext Result.Try {
      logcat(priority = LogPriority.VERBOSE) { "suspendConvertWithJsonAdapter() url='${request.url}' start" }
      val response = suspendCall(request).unwrap()
      logcat(priority = LogPriority.VERBOSE) { "suspendConvertWithJsonAdapter() url='${request.url}' end" }

      if (!response.isSuccessful) {
        throw BadStatusResponseException(response.code)
      }

      val body = response.body
      if (body == null) {
        throw EmptyBodyResponseException()
      }

      return@Try body.useBufferedSource { bufferedSource -> adapter.fromJson(bufferedSource) as T }
        ?: throw JsonConversionException("Failed to convert json with adapter: ${adapter::class.java.simpleName}")
    }
  }
}

internal inline fun <T : Any?> ResponseBody.useBufferedSource(useFunc: (BufferedSource) -> T): T {
  return byteStream().use { inputStream ->
    return@use inputStream.useBufferedSource(useFunc)
  }
}

internal inline fun <T : Any?> OutputStream.useBufferedSink(useFunc: (BufferedSink) -> T): T {
  return sink().buffer().use { bufferedSink ->
    return@use useFunc(bufferedSink)
  }
}


internal inline fun <T : Any?> InputStream.useBufferedSource(useFunc: (BufferedSource) -> T): T {
  return source().use { source ->
    return@use source.buffer().use { buffer ->
      return@use useFunc(buffer)
    }
  }
}

internal fun <T> CancellableContinuation<T>.resumeValueSafe(value: T) {
  if (isActive) {
    resume(value)
  }
}

internal inline fun <T> Result.Companion.Try(func: () -> T): Result<T> {
  return try {
    Result.success(func())
  } catch (error: Throwable) {
    Result.failure(error)
  }
}

internal fun <T> Result<T>.unwrap(): T {
  return getOrThrow()
}

@JvmOverloads
internal fun Throwable.errorMessageOrClassName(userReadable: Boolean = false): String {
  val actualMessage = if (message?.isNotNullNorBlank() == true) {
    message
  } else {
    cause?.message
  }

  if (userReadable && actualMessage.isNotNullNorBlank()) {
    return actualMessage
  }

  if (!isExceptionImportant()) {
    return this::class.java.simpleName
  }

  val exceptionClassName = this::class.java.simpleName

  if (!actualMessage.isNullOrBlank()) {
    if (actualMessage.contains(exceptionClassName)) {
      return actualMessage
    }

    return "${exceptionClassName}: ${actualMessage}"
  }

  return exceptionClassName
}

internal fun Throwable.asLogIfImportantOrErrorMessage(): String {
  if (isExceptionImportant()) {
    return asLog()
  }

  return errorMessageOrClassName()
}

internal fun Throwable.isExceptionImportant(): Boolean {
  return when (this) {
    is CancellationException,
    is SocketTimeoutException,
    is TimeoutException,
    is InterruptedIOException,
    is InterruptedException,
    is BadStatusResponseException,
    is EmptyBodyResponseException,
    is SSLException -> false
    else -> true
  }
}


@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
internal inline fun CharSequence?.isNotNullNorEmpty(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorEmpty != null)
  }

  return this != null && this.length > 0
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
internal inline fun CharSequence?.isNotNullNorBlank(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorBlank != null)
  }

  return this != null && this.isNotBlank()
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
internal inline fun String?.isNotNullNorEmpty(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorEmpty != null)
  }

  return this != null && this.length > 0
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
internal inline fun String?.isNotNullNorBlank(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorBlank != null)
  }

  return this != null && this.isNotBlank()
}

@Suppress("ReplaceSizeCheckWithIsNotEmpty", "NOTHING_TO_INLINE")
@OptIn(ExperimentalContracts::class)
internal inline fun <T> Collection<T>?.isNotNullNorEmpty(): Boolean {
  contract {
    returns(true) implies (this@isNotNullNorEmpty != null)
  }

  return this != null && this.size > 0
}