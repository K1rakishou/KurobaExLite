package com.github.k1rakishou.kpnc.domain

import com.google.firebase.messaging.RemoteMessage

interface KPNCFirebaseServiceDelegate {
  fun onNewToken(token: String)
  fun onMessageReceived(message: RemoteMessage)
}