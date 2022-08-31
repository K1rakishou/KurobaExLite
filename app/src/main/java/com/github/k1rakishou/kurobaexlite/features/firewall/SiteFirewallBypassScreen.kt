package com.github.k1rakishou.kurobaexlite.features.firewall

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.model.FirewallType
import com.github.k1rakishou.kurobaexlite.navigation.NavigationRouter
import com.github.k1rakishou.kurobaexlite.ui.helpers.ScreenCallbackStorage
import com.github.k1rakishou.kurobaexlite.ui.helpers.base.ScreenKey
import com.github.k1rakishou.kurobaexlite.ui.helpers.floating.FloatingComposeScreen
import kotlinx.coroutines.CompletableDeferred
import logcat.logcat

class SiteFirewallBypassScreen(
  screenArgs: Bundle? = null,
  componentActivity: ComponentActivity,
  navigationRouter: NavigationRouter
) : FloatingComposeScreen(screenArgs, componentActivity, navigationRouter) {
  override val screenKey: ScreenKey = SCREEN_KEY

  private val firewallType by requireArgumentLazy<FirewallType>(FIREWALL_TYPE)
  private val urlToOpen by requireArgumentLazy<String>(URL_TO_OPEN)

  private val bypassResultCompletableDeferred = CompletableDeferred<BypassResult>()
  private val cookieManager by lazy { CookieManager.getInstance() }
  private val webClient by lazy { createWebClient() }

  private var webView: WebView? = null
  private var resultNotified: Boolean = false

  private fun createWebClient(): BypassWebClient {
    check(!bypassResultCompletableDeferred.isCompleted) { "bypassResultCompletableDeferred already completed!" }

    return when (firewallType) {
      FirewallType.Cloudflare -> {
        CloudFlareCheckBypassWebClient(
          originalRequestUrlHost = urlToOpen,
          cookieManager = cookieManager,
          bypassResultCompletableDeferred = bypassResultCompletableDeferred
        )
      }
      FirewallType.YandexSmartCaptcha -> {
        YandexSmartCaptchaCheckBypassWebClient(
          originalRequestUrlHost = urlToOpen,
          cookieManager = cookieManager,
          bypassResultCompletableDeferred = bypassResultCompletableDeferred
        )
      }
    }
  }

  override fun onDisposed(screenDisposeEvent: ScreenDisposeEvent) {
    webClient.destroy()
    webView?.stopLoading()

    if (!bypassResultCompletableDeferred.isCompleted) {
      bypassResultCompletableDeferred.complete(BypassResult.Canceled)
      notifyAboutResult(BypassResult.Canceled)
    }

    super.onDisposed(screenDisposeEvent)
  }

  @Composable
  override fun FloatingContent() {
    super.FloatingContent()

    val context = LocalContext.current
    val webClientLocal = webClient
    val urlToOpenLocal = urlToOpen

    var webViewMut by remember { mutableStateOf<WebView?>(null) }
    val webViewLocal = webViewMut
    webView = webViewMut

    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = {
        val wv = WebView(context)
        wv.layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        webViewMut = wv
        logcat(TAG) { "WebView created" }

        return@AndroidView wv
      }
    )

    if (webViewLocal == null) {
      return
    }

    LaunchedEffect(
      key1 = Unit,
      block = {
        logcat(TAG) { "initWebViewAndLoadPage()" }

        initWebViewAndLoadPage(
          webView = webViewLocal,
          webClient = webClientLocal,
          urlToOpen = urlToOpenLocal,
          context = context
        )
      }
    )

  }

  @SuppressLint("SetJavaScriptEnabled")
  private suspend fun initWebViewAndLoadPage(
    webView: WebView,
    webClient: WebViewClient,
    urlToOpen: String,
    context: Context
  ) {
    webView.stopLoading()
    webView.clearCache(true)
    webView.clearFormData()
    webView.clearHistory()
    webView.clearMatches()

    val webViewDatabase = WebViewDatabase.getInstance(context.applicationContext)
    webViewDatabase.clearFormData()
    webViewDatabase.clearHttpAuthUsernamePassword()
    WebStorage.getInstance().deleteAllData()

    cookieManager.removeAllCookie()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    val webSettings: WebSettings = webView.settings
    // How do you pass anti-bot checks without javascript in modern day and age?
    webSettings.javaScriptEnabled = true
    webSettings.useWideViewPort = true
    webSettings.loadWithOverviewMode = true
    webSettings.userAgentString = appSettings.userAgent.read()
    webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
    webSettings.domStorageEnabled = true
    webSettings.databaseEnabled = true

    webView.webViewClient = webClient
    webView.loadUrl(urlToOpen)

    waitAndHandleResult()
  }

  private suspend fun waitAndHandleResult() {
    val cookieResult = bypassResultCompletableDeferred.await()
    webView?.stopLoading()

    when (cookieResult) {
      is BypassResult.Cookie -> {
        logcat(TAG) { "waitAndHandleResult() Success: ${cookieResult.cookie}" }
        snackbarManager.toast("Successfully passed firewall ${firewallType}")
      }
      is BypassResult.Error -> {
        logcatError(TAG) { "waitAndHandleResult() Error: ${cookieResult.exception.errorMessageOrClassName()}" }
        snackbarManager.errorToast("Failed to pass firewall ${firewallType}, error: ${cookieResult.exception.errorMessageOrClassName()}")
      }
      BypassResult.Canceled -> {
        logcatError(TAG) { "waitAndHandleResult() Canceled" }
        snackbarManager.toast("Canceled")
      }
      BypassResult.NotSupported -> {
        logcatError(TAG) { "waitAndHandleResult() NotSupported" }
        snackbarManager.errorToast("Firewall ${firewallType} is not supported")
      }
    }

    notifyAboutResult(cookieResult)
  }

  private fun notifyAboutResult(bypassResult: BypassResult) {
    if (!resultNotified) {
      resultNotified = true

      ScreenCallbackStorage.invokeCallback(screenKey, ON_RESULT, bypassResult)
      stopPresenting()
    }
  }

  companion object {
    private const val TAG = "SiteFirewallBypassScreen"

    val SCREEN_KEY = ScreenKey("SiteFirewallBypassScreen")

    const val FIREWALL_TYPE = "firewall_type"
    const val URL_TO_OPEN = "url_to_open"

    const val ON_RESULT = "on_result"
  }
}