package com.github.k1rakishou.kpnc.model.data.network

import com.github.k1rakishou.kpnc.BuildConfig

enum class ApplicationType(val value: Int) {
  KurobaExLiteDebug(0),
  KurobaExLiteProduction(1);

  companion object {
    fun fromFlavor(): ApplicationType {
      return when (val flavorType = BuildConfig.FLAVOR_TYPE) {
        0 -> KurobaExLiteDebug
        1 -> KurobaExLiteProduction
        else -> error("Unknown FLAVOR_TYPE: ${flavorType}")
      }
    }
  }
}