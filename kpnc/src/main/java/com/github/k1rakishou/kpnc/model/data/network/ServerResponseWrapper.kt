package com.github.k1rakishou.kpnc.model.data.network

import com.github.k1rakishou.kpnc.model.ServerErrorException

abstract class ServerResponseWrapper<T> {
  abstract val data: T?
  abstract val error: String?

  fun dataOrThrow(): T {
    if (error != null) {
      throw ServerErrorException(error!!)
    }

    return checkNotNull(data) { "Invalid server response! Both data and error are null!" }
  }
}