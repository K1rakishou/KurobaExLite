package com.github.k1rakishou.kurobaexlite.managers

import android.os.SystemClock
import com.github.k1rakishou.kurobaexlite.helpers.asFormattedToken
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import logcat.logcat

class CaptchaManager {
  private val _captchaRequestsFlow = MutableSharedFlow<CaptchaRequest>(extraBufferCapacity = Channel.UNLIMITED)
  val captchaRequestsFlow: SharedFlow<CaptchaRequest>
    get() = _captchaRequestsFlow.asSharedFlow()

  suspend fun getOrRequestCaptcha(chanDescriptor: ChanDescriptor): Captcha {
    logcat(TAG) { "getOrRequestCaptcha($chanDescriptor)" }

    val completableDeferred = CompletableDeferred<Captcha>()

    val captchaRequest = CaptchaRequest(
      chanDescriptor = chanDescriptor,
      completableDeferred = completableDeferred
    )

    logcat(TAG) { "getOrRequestCaptcha($chanDescriptor) await() start" }
    _captchaRequestsFlow.emit(captchaRequest)
    val captcha = try {
      completableDeferred.await()
    } catch (error: Throwable) {
      logcat(TAG) { "getOrRequestCaptcha($chanDescriptor) await() error: ${error.errorMessageOrClassName()}" }
      throw error
    }

    logcat(TAG) { "getOrRequestCaptcha($chanDescriptor) await() end, captcha: ${captcha}" }
    return captcha
  }

  companion object {
    private const val TAG = "CaptchaManager"
  }
}

class CaptchaRequest(
  val chanDescriptor: ChanDescriptor,
  val completableDeferred: CompletableDeferred<Captcha>
)

data class Captcha private constructor(
  val solution: CaptchaSolution,
  val validUntilMs: Long
) {
  fun isValid(time: Long) = (validUntilMs - MIN_VALID_CAPTCHA_TIME) > time

  companion object {
    fun newSolvedCaptcha(captchaSolution: CaptchaSolution, ttlMs: Long): Captcha {
      return Captcha(
        solution = captchaSolution,
        validUntilMs = SystemClock.elapsedRealtime() + ttlMs
      )
    }

    private val MIN_VALID_CAPTCHA_TIME = TimeUnit.SECONDS.toMillis(5)
  }
}

sealed class CaptchaSolution {

  data class ChallengeWithSolution(val challenge: String, val solution: String) : CaptchaSolution() {
    fun is4chanNoopChallenge(): Boolean {
      return challenge.equals(NOOP_CHALLENGE, ignoreCase = true)
    }

    override fun toString(): String {
      return "ChallengeWithSolution{challenge=${challenge.asFormattedToken()}, solution=$solution}"
    }

    companion object {
      const val NOOP_CHALLENGE = "noop"
    }
  }

}