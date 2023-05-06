package com.github.k1rakishou.kpnc.domain

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.k1rakishou.kpnc.AppConstants
import com.github.k1rakishou.kpnc.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kpnc.helpers.isNotNullNorBlank
import com.github.k1rakishou.kpnc.helpers.logcatDebug
import com.github.k1rakishou.kpnc.helpers.logcatError
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.Executors

class KPNCFirebaseServiceDelegateImpl(
  private val sharedPrefs: SharedPreferences,
  private val tokenUpdater: TokenUpdater,
  private val messageProcessor: MessageReceiver
) : KPNCFirebaseServiceDelegate {
  private val executor = Executors.newSingleThreadExecutor()

  init {
    executor.execute {
      sharedPrefs.getString(AppConstants.PrefKeys.TOKEN, null)
        ?.let { token ->
          tokenUpdater.updateToken(null, null, token)
            .onFailure { error ->
              logcatError(TAG) { "tokenUpdater.updateToken() " +
                "error: ${error.asLogIfImportantOrErrorMessage()}" }
            }
        }
    }
  }

  override fun onNewToken(token: String) {
    val prevToken = sharedPrefs.getString(AppConstants.PrefKeys.TOKEN, null)
      ?.takeIf { it.isNotNullNorBlank() }

    if (prevToken == token) {
      logcatDebug(TAG) { "onNewToken() prevToken == token" }
      return
    }

    sharedPrefs.edit { putString(AppConstants.PrefKeys.TOKEN, token) }

    tokenUpdater.reset()
    tokenUpdater.updateToken(null, null, token)
      .onFailure { error ->
        logcatError(TAG) { "tokenUpdater.updateToken() " +
          "error: ${error.asLogIfImportantOrErrorMessage()}" }
      }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    tokenUpdater.awaitUntilTokenUpdated()

    messageProcessor.onGotNewMessage(
      data = message.data[AppConstants.MessageKeys.MESSAGE_BODY]
    )
  }

  companion object {
    private const val TAG = "KPNCFirebaseServiceDelegateImpl"
  }

}