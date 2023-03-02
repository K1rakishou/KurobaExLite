package com.github.k1rakishou.kurobaexlite.features.screenshot

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.useBufferedSink
import com.github.k1rakishou.kurobaexlite.model.ClientException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okio.source
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File


class PostScreenshotScreenViewModel(
  private val androidHelpers: AndroidHelpers
) : BaseViewModel() {

  suspend fun performScreenshot(
    appContext: Context,
    postScreenshot: PostScreenshot,
    scrollContent: suspend () -> Int
  ): Result<Boolean> {
    return Result.Try {
      coroutineScope {
        var requiredHeight: Int? = null
        val chunks = mutableListOf<File>()

        try {
          val outputDirectory = File(appContext.cacheDir, "screenshot_chunks")
          if (!outputDirectory.exists()) {
            if (!outputDirectory.mkdir()) {
              throw ScreenshotSaveException("Cannot create output directory \'${outputDirectory.absolutePath}\'")
            }
          } else {
            outputDirectory.listFiles()?.forEach { file -> file.delete() }
          }

          do {
            val bitmapResult = postScreenshot.performScreenshot(requiredHeight)
            if (bitmapResult.isFailure) {
              logcatError(TAG) {
                "postScreenshot.performScreenshot() " +
                  "error: ${bitmapResult.exceptionOrThrow().asLogIfImportantOrErrorMessage()}"
              }

              break
            }

            val bitmap = bitmapResult.getOrThrow()
            if (bitmap == null) {
              logcatError(TAG) { "postScreenshot.performScreenshot() bitmap == null" }
              break
            }

            val fileName = "screenshot_chunk_(${System.nanoTime()}).png"
            chunks += withContext(Dispatchers.IO) { dumpScreenshotPartToDisk(outputDirectory, bitmap, fileName) }

            if (!bitmap.isRecycled) {
              bitmap.recycle()
            }

            val scrolled = scrollContent()
            requiredHeight = scrolled.takeIf { it > 0 }
          } while (isActive && scrolled > 0)

          withContext(Dispatchers.IO) {
            val fileName = "KurobaExLite_Screenshot_${formatter.print(DateTime.now())}.png"
            val bitmap = concatenateBitmaps(chunks)

            try {
              if (androidHelpers.isAndroidQ()) {
                saveScreenshotAndroidQAndAbove(appContext, bitmap, fileName)
              } else {
                saveScreenshotAndroidPAndBelow(bitmap, fileName)
              }
            } finally {
              if (!bitmap.isRecycled) {
                bitmap.recycle()
              }
            }
          }
        } finally {
          chunks.forEach { chunk -> chunk.delete() }
        }
      }

      return@Try true
    }.onFailure { error ->
      val isAndroidQ = androidHelpers.isAndroidQ()
      logcatError(TAG) {
        "Failed to store bitmap on disk (isAndroidQ: $isAndroidQ): ${error.asLogIfImportantOrErrorMessage()}"
      }
    }
  }

  private fun concatenateBitmaps(chunks: List<File>): Bitmap {
    var totalHeight = 0
    var totalWidth = 0

    chunks.forEach { chunk ->
      val options = BitmapFactory.Options()
      options.inJustDecodeBounds = true
      BitmapFactory.decodeFile(chunk.absolutePath, options)

      totalHeight += options.outHeight
      totalWidth = Math.max(totalWidth, options.outWidth)
    }

    val combined = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(combined)

    var currentTop = 0f

    for (chunk in chunks) {
      try {
        val bitmap = BitmapFactory.decodeFile(chunk.absolutePath, null)
        canvas.drawBitmap(bitmap, 0f, currentTop, null)
        currentTop += bitmap.height

        if (!bitmap.isRecycled) {
          bitmap.recycle()
        }
      } catch (error: Throwable) {
        break
      }
    }

    return combined
  }

  private fun dumpScreenshotPartToDisk(outputDirectory: File, bitmap: Bitmap, fileName: String): File {
    val outputFile = File(outputDirectory, fileName)

    if (outputFile.exists()) {
      outputFile.delete()
    }

    if (!outputFile.createNewFile()) {
      throw ScreenshotSaveException("Cannot create output file \'${outputFile.absolutePath}\'")
    }

    try {
      if (!outputFile.canWrite()) {
        throw ScreenshotSaveException("Cannot write to output file \'${outputFile.absolutePath}\'")
      }

      outputFile.outputStream().useBufferedSink { bufferedSink ->
        ByteArrayOutputStream().use { byteOutputStream ->
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteOutputStream)

          ByteArrayInputStream(byteOutputStream.toByteArray()).use { byteInputStream ->
            bufferedSink.writeAll(byteInputStream.source())
          }
        }
      }
    } catch (error: Throwable) {
      outputFile.delete()
    }

    return outputFile
  }

  @Suppress("DEPRECATION")
  private fun saveScreenshotAndroidPAndBelow(
    bitmap: Bitmap,
    fileName: String
  ) {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    if (!downloadsDir.exists()) {
      throw ScreenshotSaveException("Directory \'${downloadsDir.absolutePath}\' is inaccessible")
    }

    val outputFile = File(downloadsDir, fileName)
    if (!outputFile.exists()) {
      if (!outputFile.createNewFile()) {
        throw ScreenshotSaveException("Cannot create output file \'${outputFile.absolutePath}\'")
      }
    }

    if (!outputFile.canWrite()) {
      throw ScreenshotSaveException("Cannot write to output file \'${outputFile.absolutePath}\'")
    }

    outputFile.outputStream().useBufferedSink { bufferedSink ->
      ByteArrayOutputStream().use { byteOutputStream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteOutputStream)

        ByteArrayInputStream(byteOutputStream.toByteArray()).use { byteInputStream ->
          bufferedSink.writeAll(byteInputStream.source())
        }
      }
    }
  }

  @SuppressLint("Recycle")
  @RequiresApi(Build.VERSION_CODES.Q)
  private fun saveScreenshotAndroidQAndAbove(
    appContext: Context,
    bitmap: Bitmap,
    fileName: String
  ) {
    val contentResolver = appContext.contentResolver

    val contentValues = ContentValues()
    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)

    val uri = contentResolver.insert(
      MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
      contentValues
    ) ?: throw ScreenshotSaveException("contentResolver.insert() returned null")

    contentResolver.openOutputStream(uri)
      ?.useBufferedSink { bufferedSink ->
        ByteArrayOutputStream().use { byteOutputStream ->
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteOutputStream)

          ByteArrayInputStream(byteOutputStream.toByteArray()).use { byteInputStream ->
            bufferedSink.writeAll(byteInputStream.source())
          }
        }
      }
      ?: throw ScreenshotSaveException("contentResolver.openOutputStream() returned null")
  }

  class ScreenshotSaveException(message: String) : ClientException("Failed to save screenshot on disk, reason: $message")

  companion object {
    private const val TAG = "PostScreenshotScreenViewModel"
    private const val mimeType = "image/png"

    private val formatter = DateTimeFormatterBuilder()
      .append(DateTimeFormatterBuilder().appendYear(4, 9).toFormatter())
      .appendLiteral('_')
      .append(DateTimeFormatterBuilder().appendMonthOfYear(2).toFormatter())
      .appendLiteral('_')
      .append(DateTimeFormatterBuilder().appendDayOfMonth(2).toFormatter())
      .appendLiteral('_')
      .append(DateTimeFormatterBuilder().appendHourOfDay(2).toFormatter())
      .appendLiteral('_')
      .append(DateTimeFormatterBuilder().appendMinuteOfHour(2).toFormatter())
      .appendLiteral('_')
      .append(DateTimeFormatterBuilder().appendSecondOfMinute(2).toFormatter())
      .toFormatter()
  }

}