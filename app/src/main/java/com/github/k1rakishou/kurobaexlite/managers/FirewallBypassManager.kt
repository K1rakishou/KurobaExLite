package com.github.k1rakishou.kurobaexlite.managers

import androidx.annotation.GuardedBy
import com.github.k1rakishou.kurobaexlite.helpers.executors.RendezvousCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.util.awaitSilently
import com.github.k1rakishou.kurobaexlite.model.FirewallType
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import logcat.logcat
import okhttp3.HttpUrl

class FirewallBypassManager(
  private val appScope: CoroutineScope,
  private val applicationVisibilityManager: ApplicationVisibilityManager
) {
  @GuardedBy("itself")
  private val firewallSiteInfoMap = mutableMapOf<String, FirewallSiteInfo>()

  private val rendezvousCoroutineExecutor = RendezvousCoroutineExecutor(appScope)

  private val _showFirewallControllerEvents = MutableSharedFlow<ShowFirewallControllerInfo>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )
  val showFirewallControllerEvents: SharedFlow<ShowFirewallControllerInfo>
    get() = _showFirewallControllerEvents.asSharedFlow()

  private val _firewallBypassedEvents = MutableSharedFlow<FirewallBypassedInfo>(
    extraBufferCapacity = Channel.UNLIMITED,
  )
  val firewallBypassedEvents: SharedFlow<FirewallBypassedInfo>
    get() = _firewallBypassedEvents.asSharedFlow()

  fun onFirewallDetected(
    firewallType: FirewallType,
    siteKey: SiteKey,
    urlToOpen: HttpUrl,
    originalRequestUrl: HttpUrl,
    cookieMap: Map<String, String>
  ) {
    if (!applicationVisibilityManager.isAppInForeground()) {
      // No point to do anything here since there is most likely no activity currently alive
      return
    }

    val host = urlToOpen.host

    val notifyListeners = synchronized(firewallSiteInfoMap) {
      val firewallSiteInfo = firewallSiteInfoMap.getOrPut(
        key = host,
        defaultValue = { FirewallSiteInfo(lastCheckTime = 0) }
      )

      if (firewallSiteInfo.isCurrentlyShowing) {
        return@synchronized false
      }

      val now = System.currentTimeMillis()
      if (now - firewallSiteInfo.lastCheckTime < FIREWALL_CHECK_TIMEOUT_MS) {
        return@synchronized false
      }

      return@synchronized true
    }

    if (!notifyListeners) {
      return
    }

    logcat(TAG) { "Sending event to show SiteFirewallBypassController" }

    rendezvousCoroutineExecutor.post {
      val completableDeferred = CompletableDeferred(Unit)

      val showFirewallControllerInfo = ShowFirewallControllerInfo(
        firewallType = firewallType,
        siteKey = siteKey,
        urlToOpen = urlToOpen,
        originalRequestUrl = originalRequestUrl,
        cookieMap = cookieMap,
        onFinished = completableDeferred
      )

      _showFirewallControllerEvents.emit(showFirewallControllerInfo)

      completableDeferred.awaitSilently()

      synchronized(firewallSiteInfoMap) {
        val firewallSiteInfo = firewallSiteInfoMap.getOrPut(
          key = host,
          defaultValue = { FirewallSiteInfo(lastCheckTime = 0) }
        )

        firewallSiteInfo.lastCheckTime = System.currentTimeMillis()
      }
    }
  }

  fun onFirewallBypassed(
    firewallType: FirewallType,
    siteKey: SiteKey,
    urlToOpen: HttpUrl,
    originalRequestUrl: HttpUrl
  ) {
    val firewallBypassedInfo = FirewallBypassedInfo(
      firewallType = firewallType,
      siteKey = siteKey,
      urlToOpen = urlToOpen,
      originalRequestUrl = originalRequestUrl,
    )

    _firewallBypassedEvents.tryEmit(firewallBypassedInfo)
  }

  data class FirewallSiteInfo(
    var lastCheckTime: Long = 0L,
    var isCurrentlyShowing: Boolean = false
  )

  data class ShowFirewallControllerInfo(
    val firewallType: FirewallType,
    val siteKey: SiteKey,
    val urlToOpen: HttpUrl,
    val originalRequestUrl: HttpUrl,
    val cookieMap: Map<String, String>,
    val onFinished: CompletableDeferred<Unit>
  )

  data class FirewallBypassedInfo(
    val firewallType: FirewallType,
    val siteKey: SiteKey,
    val urlToOpen: HttpUrl,
    val originalRequestUrl: HttpUrl
  )

  companion object {
    private const val TAG = "FirewallBypassManager"
    private const val FIREWALL_CHECK_TIMEOUT_MS = 10_000L
  }

}