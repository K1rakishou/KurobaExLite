package com.github.k1rakishou.kurobaexlite.ui.helpers

import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import java.lang.ref.WeakReference

object ScreenCallbackStorage {
  private val callbackStorage = mutableMapOf<ScreenKey, MutableMap<String, WeakReference<IRememberableCallback>>>()

  fun onScreenDisposed(screenKey: ScreenKey) {
    callbackStorage.remove(screenKey)
  }

  fun <T : IRememberableCallback> rememberCallback(screenKey: ScreenKey, callbackKey: String, callback: T): T {
    val callbacksForScreen = callbackStorage.getOrPut(key = screenKey, defaultValue = { mutableMapOf() })
    callbacksForScreen[callbackKey] = WeakReference(callback)

    return callback
  }

  fun invokeCallback(screenKey: ScreenKey, callbackKey: String) {
    val callback: IRememberableCallback = callbackStorage[screenKey]?.get(callbackKey)?.get()
      ?: return

    (callback as RememberableCallback0).invoke()
  }

  fun <T1 : Any> invokeCallback(screenKey: ScreenKey, callbackKey: String, p1: T1) {
    val callback: IRememberableCallback = callbackStorage[screenKey]?.get(callbackKey)?.get()
      ?: return

    (callback as RememberableCallback1<T1>).invoke(p1)
  }

  fun <T1 : Any, T2: Any> invokeCallback(screenKey: ScreenKey, callbackKey: String, p1: T1, p2: T2) {
    val callback: IRememberableCallback = callbackStorage[screenKey]?.get(callbackKey)?.get()
      ?: return

    (callback as RememberableCallback2<T1, T2>).invoke(p1, p2)
  }

  interface IRememberableCallback

  fun interface RememberableCallback0 : IRememberableCallback {
    fun invoke()
  }

  fun interface RememberableCallback1<T1 : Any> : IRememberableCallback {
    fun invoke(p1: T1)
  }

  fun interface RememberableCallback2<T1 : Any, T2: Any> : IRememberableCallback {
    fun invoke(p1: T1, p2: T2)
  }

}