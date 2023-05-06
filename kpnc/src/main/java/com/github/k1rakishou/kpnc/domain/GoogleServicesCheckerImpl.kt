package com.github.k1rakishou.kpnc.domain

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class GoogleServicesCheckerImpl(
  private val applicationContext: Context
) : GoogleServicesChecker {

  @Suppress("MoveVariableDeclarationIntoWhen")
  override fun checkGoogleServicesStatus(): GoogleServicesChecker.Result {
    val result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(applicationContext)

    return when (result) {
      ConnectionResult.SUCCESS -> GoogleServicesChecker.Result.Success
      ConnectionResult.SERVICE_MISSING -> GoogleServicesChecker.Result.ServiceMissing
      ConnectionResult.SERVICE_UPDATING -> GoogleServicesChecker.Result.ServiceUpdating
      ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> GoogleServicesChecker.Result.ServiceUpdateRequired
      ConnectionResult.SERVICE_DISABLED -> GoogleServicesChecker.Result.ServiceDisabled
      ConnectionResult.SERVICE_INVALID -> GoogleServicesChecker.Result.ServiceInvalid
      else -> GoogleServicesChecker.Result.Unknown
    }
  }

}