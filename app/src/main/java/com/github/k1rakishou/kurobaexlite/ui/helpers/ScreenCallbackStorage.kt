package com.github.k1rakishou.kurobaexlite.ui.helpers

import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey

object ScreenCallbackStorage {
  private val callbackStorage = mutableMapOf<ScreenKey, MutableMap<String, IRememberableCallback>>()

  fun onScreenDisposed(screenKey: ScreenKey) {
    callbackStorage.remove(screenKey)
  }

  fun <T : IRememberableCallback> rememberCallback(screenKey: ScreenKey, callbackKey: String, callback: T): T {
    val callbacksForScreen = callbackStorage.getOrPut(key = screenKey, defaultValue = { mutableMapOf() })
    callbacksForScreen[callbackKey] = callback

    return callback
  }

  fun invokeCallback(screenKey: ScreenKey, callbackKey: String) {
    val callback: IRememberableCallback = callbackStorage[screenKey]?.get(callbackKey)
      ?: return

    (callback as RememberableCallback0).invoke()
  }

  fun <T1 : Any?> invokeCallback(screenKey: ScreenKey, callbackKey: String, p1: T1) {
    val callback: IRememberableCallback = callbackStorage[screenKey]?.get(callbackKey)
      ?: return

    (callback as RememberableCallback1<T1>).invoke(p1)
  }

  fun <T1 : Any, T2: Any> invokeCallback(screenKey: ScreenKey, callbackKey: String, p1: T1, p2: T2) {
    val callback: IRememberableCallback = callbackStorage[screenKey]?.get(callbackKey)
      ?: return

    (callback as RememberableCallback2<T1, T2>).invoke(p1, p2)
  }

  fun <R : Any> invokeCallbackWithResult(screenKey: ScreenKey, callbackKey: String): R? {
    val callback = callbackStorage[screenKey]?.get(callbackKey) as RememberableCallbackWithResult0<R>?
      ?: return null

    return callback.invoke()
  }

  fun <T1 : Any?, R : Any> invokeCallbackWithResult(screenKey: ScreenKey, callbackKey: String, p1: T1): R? {
    val callback = callbackStorage[screenKey]?.get(callbackKey) as RememberableCallbackWithResult1<T1, R>?
      ?: return null

    return callback.invoke(p1)
  }

  interface IRememberableCallback

  fun interface RememberableCallback0 : IRememberableCallback {
    fun invoke()
  }

  fun interface RememberableCallback1<T1 : Any?> : IRememberableCallback {
    fun invoke(p1: T1)
  }

  fun interface RememberableCallback2<T1 : Any, T2: Any> : IRememberableCallback {
    fun invoke(p1: T1, p2: T2)
  }

  interface IRememberableCallbackWithResult<R : Any?> : IRememberableCallback

  fun interface RememberableCallbackWithResult0<R : Any?> : IRememberableCallbackWithResult<R> {
    fun invoke(): R
  }

  fun interface RememberableCallbackWithResult1<T1 : Any?, R: Any?> : IRememberableCallbackWithResult<R> {
    fun invoke(p1: T1): R
  }

}