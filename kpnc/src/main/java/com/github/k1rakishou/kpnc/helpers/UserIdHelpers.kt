package com.github.k1rakishou.kpnc.helpers

fun isUserIdValid(userId: String?): Boolean {
  if (userId == null) {
    return false
  }

  return userId.length in 32..128
}