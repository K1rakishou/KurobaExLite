package com.github.k1rakishou.kpnc.helpers

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun retrieveFirebaseToken(): Result<String> {
  return suspendCancellableCoroutine<Result<String>> { cancellableContinuation ->
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
      if (!task.isSuccessful) {
        cancellableContinuation.resume(Result.failure(task.exception!!))
      } else {
        cancellableContinuation.resume(Result.success(task.result))
      }
    }
  }
}