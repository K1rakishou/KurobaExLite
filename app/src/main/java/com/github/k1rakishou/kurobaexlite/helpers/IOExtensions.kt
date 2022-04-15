package com.github.k1rakishou.kurobaexlite.helpers

import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source

suspend fun OkHttpClient.suspendCall(request: Request): Response {
  return suspendCancellableCoroutine { continuation ->
    val call = newCall(request)

    continuation.invokeOnCancellation {
      if (!call.isCanceled()) {
        call.cancel()
      }
    }

    call.enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        continuation.resumeErrorSafe(e)
      }

      override fun onResponse(call: Call, response: Response) {
        continuation.resumeValueSafe(response)
      }
    })
  }
}

suspend inline fun <reified T : Any?> OkHttpClient.suspendConvertIntoJsonObjectWithAdapter(
  request: Request,
  adapter: JsonAdapter<T>
): Result<out T?> {
  return withContext(Dispatchers.IO) {
    return@withContext Result.Try {
      logcat { "suspendConvertIntoJsonObjectWithAdapter() url='${request.url}' start" }
      val response = suspendCall(request)
      logcat { "suspendConvertIntoJsonObjectWithAdapter() url='${request.url}' end" }

      if (!response.isSuccessful) {
        throw BadStatusResponseException(response.code)
      }

      val body = response.body
      if (body == null) {
        throw EmptyBodyResponseException()
      }

      return@Try body.useBufferedSource { bufferedSource -> adapter.fromJson(bufferedSource) as T }
    }
  }
}

inline fun <T : Any?> ResponseBody.useBufferedSource(useFunc: (BufferedSource) -> T): T {
  return byteStream().use { inputStream ->
    return@use inputStream.useBufferedSource(useFunc)
  }
}

inline fun <T : Any?> OutputStream.useBufferedSink(useFunc: (BufferedSink) -> T): T {
  return sink().buffer().use { bufferedSink ->
    return@use useFunc(bufferedSink)
  }
}


inline fun <T : Any?> InputStream.useBufferedSource(useFunc: (BufferedSource) -> T): T {
  return source().use { source ->
    return@use source.buffer().use { buffer ->
      return@use useFunc(buffer)
    }
  }
}

inline fun <T : Any?> ResponseBody.useJsonReader(useFunc: (JsonReader) -> T): T {
  return source().use { source ->
    return source.buffer.use { bufferedSource ->
      return@use JsonReader.of(bufferedSource).use { jsonReader ->
        return@use useFunc(jsonReader)
      }
    }
  }
}
