package com.github.k1rakishou.kurobaexlite.ui.helpers.base

import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage

class CallbackBuilder(
  private val screenKey: ScreenKey
) {

  fun callback(callbackKey: String, func: () -> Unit) {
    val rememberableCallback = ScreenCallbackStorage.RememberableCallback0(func)

    ScreenCallbackStorage.rememberCallback(
      screenKey = screenKey,
      callbackKey = callbackKey,
      callback = rememberableCallback as ScreenCallbackStorage.IRememberableCallback
    )
  }

  fun <T1 : Any> callback(callbackKey: String, func: (T1) -> Unit) {
    val rememberableCallback = ScreenCallbackStorage.RememberableCallback1(func)

    ScreenCallbackStorage.rememberCallback(
      screenKey = screenKey,
      callbackKey = callbackKey,
      callback = rememberableCallback as ScreenCallbackStorage.IRememberableCallback
    )
  }

  fun <T1 : Any, T2: Any> callback(callbackKey: String, func: (T1, T2) -> Unit) {
    val rememberableCallback = ScreenCallbackStorage.RememberableCallback2(func)

    ScreenCallbackStorage.rememberCallback(
      screenKey = screenKey,
      callbackKey = callbackKey,
      callback = rememberableCallback as ScreenCallbackStorage.IRememberableCallback
    )
  }

  fun <R : Any?> callbackWithResult(callbackKey: String, func: () -> R) {
    val rememberableCallback = ScreenCallbackStorage.RememberableCallbackWithResult0(func)

    ScreenCallbackStorage.rememberCallback(
      screenKey = screenKey,
      callbackKey = callbackKey,
      callback = rememberableCallback as ScreenCallbackStorage.IRememberableCallback
    )
  }

  fun <T1 : Any?, R : Any?> callbackWithResult(callbackKey: String, func: (T1) -> R) {
    val rememberableCallback = ScreenCallbackStorage.RememberableCallbackWithResult1(func)

    ScreenCallbackStorage.rememberCallback(
      screenKey = screenKey,
      callbackKey = callbackKey,
      callback = rememberableCallback as ScreenCallbackStorage.IRememberableCallback
    )
  }

}
