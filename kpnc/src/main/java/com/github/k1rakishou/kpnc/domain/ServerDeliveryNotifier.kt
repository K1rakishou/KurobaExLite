package com.github.k1rakishou.kpnc.domain

interface ServerDeliveryNotifier {
  fun notifyPostUrlsDelivered(replyMessageIds: List<Long>): Result<Unit>
}