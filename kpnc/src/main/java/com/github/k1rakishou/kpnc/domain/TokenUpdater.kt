package com.github.k1rakishou.kpnc.domain

interface TokenUpdater {
  fun reset()
  fun updateToken(instanceAddress: String?, userId: String?, token: String): Result<Boolean>
  fun awaitUntilTokenUpdated()
}