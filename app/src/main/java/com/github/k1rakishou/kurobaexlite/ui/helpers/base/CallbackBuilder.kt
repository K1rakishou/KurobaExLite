package com.github.k1rakishou.kurobaexlite.ui.helpers.base

import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage

class CallbackBuilder(
  private val screenKey: ScreenKey
) {

  fun callback(callbackKey: String, func: () -> Unit) {
    val rememberableCallback = ScreenCallbackStorage.RememberableCallback0 { func() }

    ScreenCallbackStorage.rememberCallback(
      screenKey = screenKey,
      callbackKey = callbackKey,
      callback = rememberableCallback as ScreenCallbackStorage.IRememberableCallback
    )
  }

  fun <T1 : Any> callback(callbackKey: String, func: (T1) -> Unit) {
    val rememberableCallback = ScreenCallbackStorage.RememberableCallback1<T1> { p1 -> func(p1) }

    ScreenCallbackStorage.rememberCallback(
      screenKey = screenKey,
      callbackKey = callbackKey,
      callback = rememberableCallback as ScreenCallbackStorage.IRememberableCallback
    )
  }

  fun <T1 : Any, T2: Any> callback(callbackKey: String, func: (T1, T2) -> Unit) {
    val rememberableCallback = ScreenCallbackStorage.RememberableCallback2<T1, T2> { p1, p2 -> func(p1, p2) }

    ScreenCallbackStorage.rememberCallback(
      screenKey = screenKey,
      callbackKey = callbackKey,
      callback = rememberableCallback as ScreenCallbackStorage.IRememberableCallback
    )
  }

}
