package com.github.k1rakishou.kurobaexlite.helpers

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.app.ComponentActivity
import androidx.core.content.ContextCompat
import com.github.k1rakishou.kurobaexlite.helpers.util.resumeSafe
import kotlinx.coroutines.suspendCancellableCoroutine

class RuntimePermissionsHelper(
  private val applicationContext: Context,
  private val callbackActivity: OnRequestPermissionsResultCallback,
) {
  private var pendingCallback: CallbackHolder? = null

  fun hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED
  }

  suspend fun requestPermission(permission: String): Boolean {
    return suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
      requestPermission(
        permission = permission,
        callback = object : Callback {
          override fun onRuntimePermissionResult(granted: Boolean) {
            cancellableContinuation.resumeSafe(granted)
          }
        }
      )
    }
  }

  fun requestPermission(permission: String, callback: Callback): Boolean {
    if (hasPermission(permission)) {
      callback.onRuntimePermissionResult(true)
      return true
    }

    if (pendingCallback != null) {
      return false
    }

    pendingCallback = CallbackHolder().also { holder ->
      holder.callback = callback
      holder.permission = permission
    }

    ActivityCompat.requestPermissions(
      (callbackActivity as ComponentActivity),
      arrayOf(permission),
      RUNTIME_PERMISSION_RESULT_ID
    )

    return true
  }

  fun onRequestPermissionsResult(
    reqCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    if (reqCode == RUNTIME_PERMISSION_RESULT_ID && pendingCallback != null) {
      var granted = false

      for (i in permissions.indices) {
        val permission = permissions[i]
        if (permission == pendingCallback?.permission && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
          granted = true
          break
        }
      }

      pendingCallback?.callback?.onRuntimePermissionResult(granted)
      pendingCallback = null
    }
  }

  interface PermissionRequiredDialogCallback {
    fun retryPermissionRequest()
  }

  private class CallbackHolder {
    var callback: Callback? = null
    var permission: String? = null
  }

  interface Callback {
    fun onRuntimePermissionResult(granted: Boolean)
  }

  companion object {
    private const val RUNTIME_PERMISSION_RESULT_ID = 3
  }

}
