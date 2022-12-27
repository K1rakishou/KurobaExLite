package com.github.k1rakishou.kurobaexlite.helpers

import android.net.Uri
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.callback.FileChooserCallback
import com.github.k1rakishou.fsaf.callback.FileCreateCallback
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeSafe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun FileChooser.openChooseFileDialogSuspend(): Result<Uri> {
  return suspendCancellableCoroutine<Result<Uri>> { continuation ->
    openChooseFileDialog(
      object : FileChooserCallback() {
        override fun onCancel(reason: String) {
          continuation.resumeSafe(Result.failure(CancellationException(reason)))
        }

        override fun onResult(uri: Uri) {
          continuation.resumeSafe(Result.success(uri))
        }
      }
    )
  }
}

suspend fun FileChooser.openCreateFileDialogSuspend(fileName: String): Result<Uri> {
  return suspendCancellableCoroutine<Result<Uri>> { continuation ->
    openCreateFileDialog(
      fileName,
      object : FileCreateCallback() {
        override fun onCancel(reason: String) {
          continuation.resumeSafe(Result.failure(CancellationException(reason)))
        }

        override fun onResult(uri: Uri) {
          continuation.resumeSafe(Result.success(uri))
        }
      }
    )
  }
}