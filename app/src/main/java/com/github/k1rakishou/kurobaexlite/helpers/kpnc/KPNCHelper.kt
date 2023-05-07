package com.github.k1rakishou.kurobaexlite.helpers.kpnc

import android.content.SharedPreferences
import androidx.annotation.GuardedBy
import com.github.k1rakishou.kpnc.AppConstants
import com.github.k1rakishou.kpnc.helpers.isUserIdValid
import com.github.k1rakishou.kpnc.model.repository.AccountRepository
import com.github.k1rakishou.kpnc.model.repository.PostRepository
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatDebug
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import java.util.concurrent.TimeUnit

class KPNCHelper(
  private val siteManager: SiteManager,
  private val sharedPrefs: SharedPreferences,
  private val appSettings: AppSettings,
  private val accountRepository: AccountRepository,
  private val postRepository: PostRepository,
) {
  @GuardedBy("this")
  private var kpncAccountInfoCached: KPNCAccountInfoCached? = null

  suspend fun kpncAppInfo(): KPNCAppInfo {
    val now = System.currentTimeMillis()

    val kpncAppInfoFromCache = synchronized(this) {
      val kpncAccountInfoCachedLocal = kpncAccountInfoCached
      if (kpncAccountInfoCachedLocal == null) {
        logcatDebug(TAG) { "kpncAppInfo() kpncAccountInfoCachedLocal is null" }
        return@synchronized null
      }

      if (now >= kpncAccountInfoCachedLocal.validUntilMs) {
        logcatDebug(TAG) {
          "kpncAppInfo() kpncAccountInfoCachedLocal now >= kpncAccountInfoCachedLocal.validUntilMs " +
            "(${now} >= ${kpncAccountInfoCachedLocal.validUntilMs}, " +
            "delta: ${kpncAccountInfoCachedLocal.validUntilMs - now})"
        }

        return@synchronized null
      }

      if (now - kpncAccountInfoCachedLocal.lastAccountValidationTimeMs > FIFTEEN_MINUTES) {
        logcatDebug(TAG) {
          "kpncAppInfo() now - kpncAccountInfoCachedLocal.lastAccountValidationTimeMs > FIFTEEN_MINUTES " +
            "(${now - kpncAccountInfoCachedLocal.lastAccountValidationTimeMs} >= ${FIFTEEN_MINUTES})"
        }

        return@synchronized null
      }

      logcatDebug(TAG) { "kpncAppInfo() everything is OK" }

      return@synchronized KPNCAppInfo.Success(
        isAccountValid = true,
        validUntilMs = kpncAccountInfoCachedLocal.validUntilMs
      )
    }

    if (kpncAppInfoFromCache != null) {
      logcatDebug(TAG) { "kpncAppInfo() kpncAppInfoFromCache != null" }
      return kpncAppInfoFromCache
    }

    logcatDebug(TAG) { "kpncAppInfo() kpncAppInfoFromCache == null" }

    val userId = sharedPrefs.getString(AppConstants.PrefKeys.USER_ID, null)
      ?.takeIf { userId -> isUserIdValid(userId) }
    val instanceAddress = sharedPrefs.getString(AppConstants.PrefKeys.INSTANCE_ADDRESS, null)
      ?.takeIf { instanceAddress -> instanceAddress.isNotNullNorBlank() }

    if (instanceAddress == null || userId == null) {
      logcatDebug(TAG) { "Bad userId: ${userId} or instanceAddress: ${instanceAddress}" }
      return KPNCAppInfo.Error(errorMessage = "Bad userId: ${userId} or instanceAddress: ${instanceAddress}")
    }

    logcatDebug(TAG) { "InstanceAddress and UserId are OK" }

    val accountInfoResult = accountRepository.getAccountInfo(instanceAddress, userId)
    if (accountInfoResult.isFailure) {
      val exception = accountInfoResult.exceptionOrNull()!!
      logcatDebug(TAG) { "getAccountInfo() error: ${exception.asLogIfImportantOrErrorMessage()}" }

      return KPNCAppInfo.Error(exception.errorMessageOrClassName(userReadable = true))
    }

    val accountInfoResponse = accountInfoResult.getOrNull()
    if (accountInfoResponse == null) {
      return KPNCAppInfo.Error("accountInfoResponse is null")
    }

    val validUntilMs = accountInfoResponse.validUntil
    if (validUntilMs == null) {
      return KPNCAppInfo.Error("validUntil is null")
    }

    val isValid = accountInfoResponse.isValid
    logcatDebug(TAG) { "getAccountInfo() success, isValid: ${isValid}, validUntilMs: ${validUntilMs}" }

    synchronized(this) {
      kpncAccountInfoCached = KPNCAccountInfoCached(
        validUntilMs = validUntilMs,
        lastAccountValidationTimeMs = System.currentTimeMillis()
      )
    }

    return KPNCAppInfo.Success(
      isAccountValid = isValid,
      validUntilMs = validUntilMs
    )
  }

  suspend fun startWatchingPost(postDescriptor: PostDescriptor): Result<Unit> {
    if (!isKpncEnabled()) {
      return Result.success(Unit)
    }

    if (!isKpncAccountValid()) {
      logcatError(TAG) { "startWatchingPost() KPNC account is not valid" }
      return Result.failure(WatchPostError(postDescriptor, "KPNC account is not valid"))
    }

    logcatDebug(TAG) { "startWatchingPost(${postDescriptor})" }

    val site = siteManager.bySiteKey(postDescriptor.siteKey)
    if (site == null) {
      logcatError(TAG) { "startWatchingPost() site not found for key ${postDescriptor.siteKey}" }
      return Result.failure(WatchPostError(postDescriptor, "Site is not supported"))
    }

    val postUrl = site.desktopUrl(postDescriptor.threadDescriptor, postDescriptor.postNo, postDescriptor.postSubNo)
    if (postUrl.isNullOrEmpty()) {
      logcatError(TAG) { "startWatchingPost() failed to convert ${postDescriptor} into url" }
      return Result.failure(WatchPostError(postDescriptor, "Site is not supported"))
    }

    val watchPostResult = postRepository.watchPost(postUrl)
    if (watchPostResult.isFailure) {
      val exception = watchPostResult.exceptionOrNull()!!
      logcatError(TAG) { "handleStartWatchingPost(${postUrl}) error: ${exception.asLogIfImportantOrErrorMessage()}" }

      return Result.failure(exception)
    }

    val success = watchPostResult.getOrThrow()
    if (!success) {
      logcatError(TAG) { "startWatchingPost() watchPostResult.data.success == false" }
      return Result.failure(WatchPostError(postDescriptor, "KPNC returned unsuccessful result"))
    }

    logcatDebug(TAG) { "startWatchingPost() success!" }
    return Result.success(Unit)
  }

  suspend fun stopWatchingPost(postDescriptor: PostDescriptor): Result<Unit> {
    if (!isKpncEnabled()) {
      return Result.success(Unit)
    }

    if (!isKpncAccountValid()) {
      logcatError(TAG) { "stopWatchingPost() KPNC account is not valid" }
      return Result.failure(WatchPostError(postDescriptor, "KPNC account is not valid"))
    }

    logcatDebug(TAG) { "stopWatchingPost(${postDescriptor})" }

    val site = siteManager.bySiteKey(postDescriptor.siteKey)
    if (site == null) {
      logcatError(TAG) { "stopWatchingPost() site not found for key ${postDescriptor.siteKey}" }
      return Result.failure(WatchPostError(postDescriptor, "Site is not supported"))
    }

    val postUrl = site.desktopUrl(postDescriptor.threadDescriptor, postDescriptor.postNo, postDescriptor.postSubNo)
    if (postUrl.isNullOrEmpty()) {
      logcatError(TAG) { "stopWatchingPost() failed to convert ${postDescriptor} into url" }
      return Result.failure(WatchPostError(postDescriptor, "Site is not supported"))
    }

    val unwatchPostResult = postRepository.unwatchPost(postUrl)
    if (unwatchPostResult.isFailure) {
      val exception = unwatchPostResult.exceptionOrNull()!!
      logcatError(TAG) { "handleStopWatchingPost(${postUrl}) error: ${exception.asLogIfImportantOrErrorMessage()}" }

      return Result.failure(exception)
    }

    val success = unwatchPostResult.getOrThrow()
    if (!success) {
      logcatError(TAG) { "stopWatchingPost() unwatchPostResult.data.success == false" }
      return Result.failure(WatchPostError(postDescriptor, "KPNC returned unsuccessful result"))
    }

    logcatDebug(TAG) { "stopWatchingPost() success!" }
    return Result.success(Unit)
  }

  suspend fun isKpncEnabledAndAccountIsValid(): Boolean {
    return isKpncEnabled() && isKpncAccountValid()
  }

  suspend fun isKpncEnabled(): Boolean {
    return appSettings.pushNotifications.read()
  }

  private suspend fun isKpncAccountValid(): Boolean {
    if (!isKpncEnabled()) {
      return false
    }

    return (kpncAppInfo() as? KPNCAppInfo.Success)?.isAccountValid == true
  }

  open class KPNCError(message: String) : ClientException(message)

  class WatchPostError(postDescriptor: PostDescriptor, message: String)
    : KPNCError("Cannot start watching post \'${postDescriptor.asReadableString()}\': \'$message\'")

  companion object {
    private const val TAG = "KPNCHelper"

    private val FIFTEEN_MINUTES = TimeUnit.MINUTES.toMillis(15)
  }

}

private data class KPNCAccountInfoCached(
  val validUntilMs: Long,
  val lastAccountValidationTimeMs: Long
)

sealed class KPNCAppInfo {
  fun errorAsReadableString(): String? {
    return when (this) {
      is Error -> "Error while trying to check KPNC status: \'${this.errorMessage}\'"
      is Success -> null
    }
  }

  data class Error(val errorMessage: String) : KPNCAppInfo()
  data class Success(
    val isAccountValid: Boolean,
    val validUntilMs: Long
  ) : KPNCAppInfo()
}