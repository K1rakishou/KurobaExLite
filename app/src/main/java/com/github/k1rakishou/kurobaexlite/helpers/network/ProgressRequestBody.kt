package com.github.k1rakishou.kurobaexlite.helpers.network

import java.io.IOException
import java.util.concurrent.CancellationException
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer

class ProgressRequestBody(
  private val fileIndex: Int = 1,
  private val totalFiles: Int = 1,
  private val delegate: RequestBody,
  private val listener: ProgressRequestListener
) : RequestBody() {
  private var progressSink: ProgressSink? = null

  override fun contentType(): MediaType? {
    return delegate.contentType()
  }

  @Throws(IOException::class)
  override fun contentLength(): Long {
    return delegate.contentLength()
  }

  @Throws(IOException::class)
  override fun writeTo(sink: BufferedSink) {
    progressSink = ProgressSink(sink)

    val bufferedSink = progressSink!!.buffer()
    delegate.writeTo(bufferedSink)
    bufferedSink.flush()
  }

  private inner class ProgressSink(delegate: Sink) : ForwardingSink(delegate) {
    private var bytesWritten: Long = 0
    private var lastPercent = 0

    @Throws(IOException::class)
    override fun write(source: Buffer, byteCount: Long) {
      super.write(source, byteCount)

      if (bytesWritten == 0L) {
        try {
          // so we can know that the uploading has just started
          listener.onRequestProgress(0f)
        } catch (cancellationException: CancellationException) {
          throw IOException("Canceled")
        }
      }

      bytesWritten += byteCount

      if (contentLength() > 0) {
        val percent = (maxPercent * bytesWritten / contentLength()).toInt()
        if (percent - lastPercent >= percentStep) {
          lastPercent = percent

          // OkHttp will explode if the listener throws anything other than IOException
          // so we need to wrap those exceptions into IOException. For now only
          // CancellationException was found to be thrown somewhere deep inside the listener.
          try {
            listener.onRequestProgress(toOverallProgress(percent))
          } catch (cancellationException: CancellationException) {
            throw IOException("Canceled")
          }
        }
      }
    }
  }

  fun interface ProgressRequestListener {
    fun onRequestProgress(progress: Float)
  }

  private fun toOverallProgress(percent: Int): Float {
    val index = (fileIndex - 1).coerceAtLeast(0).toFloat()

    val totalProgress = totalFiles.toFloat() * 100f
    val currentProgress = (index * 100f) + percent.toFloat()

    return currentProgress / totalProgress
  }

  companion object {
    private const val maxPercent = 100
    private const val percentStep = 5
  }
}
