package com.github.k1rakishou.kpnc.domain

interface GoogleServicesChecker {
  fun checkGoogleServicesStatus(): Result

  enum class Result {
    Empty,
    Success,
    ServiceMissing,
    ServiceUpdating,
    ServiceUpdateRequired,
    ServiceDisabled,
    ServiceInvalid,
    Unknown
  }
}