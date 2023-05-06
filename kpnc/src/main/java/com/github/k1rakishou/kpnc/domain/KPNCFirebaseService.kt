package com.github.k1rakishou.kpnc.domain

import com.github.k1rakishou.kpnc.helpers.logcatDebug
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent

class KPNCFirebaseService : FirebaseMessagingService(), KoinComponent {
  private val kpncFirebaseServiceDelegate by inject<KPNCFirebaseServiceDelegate>()

  init {
    logcatDebug(TAG) { "init()" }
  }

  override fun onNewToken(token: String) {
    logcatDebug(TAG) { "onNewToken() start" }
    kpncFirebaseServiceDelegate.onNewToken(token)
    logcatDebug(TAG) { "onNewToken() end" }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    logcatDebug(TAG) { "onMessageReceived() start" }
    kpncFirebaseServiceDelegate.onMessageReceived(message)
    logcatDebug(TAG) { "onMessageReceived() end" }
  }

  companion object {
    private const val TAG = "KPNCFirebaseService"
  }

}