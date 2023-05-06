package com.github.k1rakishou.kpnc.domain

interface ClientAppNotifier {
  fun onRepliesReceived(postUrls: List<String>): Result<Unit>
}