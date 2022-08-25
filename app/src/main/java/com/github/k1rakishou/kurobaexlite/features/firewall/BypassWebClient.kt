package com.github.k1rakishou.kurobaexlite.features.firewall

import android.webkit.WebViewClient
import com.github.k1rakishou.kurobaexlite.model.BypassException
import kotlinx.coroutines.CompletableDeferred

abstract class BypassWebClient(
  protected val bypassResultCompletableDeferred: CompletableDeferred<BypassResult>
) : WebViewClient() {

  protected fun success(cookieValue: String) {
    if (bypassResultCompletableDeferred.isCompleted) {
      return
    }

    bypassResultCompletableDeferred.complete(BypassResult.Cookie(cookieValue))
  }

  protected fun fail(exception: BypassException) {
    if (bypassResultCompletableDeferred.isCompleted) {
      return
    }

    bypassResultCompletableDeferred.complete(BypassResult.Error(exception))
  }

  fun destroy() {
  }

  companion object {
    const val MAX_PAGE_LOADS_COUNT = 10
  }

}

sealed class BypassResult {
  object NotSupported : BypassResult() {
    override fun toString(): String {
      return "NotSupported"
    }
  }

  object Canceled : BypassResult() {
    override fun toString(): String {
      return "Canceled"
    }
  }

  data class Cookie(val cookie: String) : BypassResult()
  data class Error(val exception: BypassException) : BypassResult()
}