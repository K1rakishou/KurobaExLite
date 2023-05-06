package com.github.k1rakishou.kpnc.domain


interface MessageReceiver {
  fun onGotNewMessage(data: String?)
}