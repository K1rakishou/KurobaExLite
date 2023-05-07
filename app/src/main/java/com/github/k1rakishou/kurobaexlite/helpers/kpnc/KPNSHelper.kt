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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class KPNSHelper(
  private val siteManager: SiteManager,
  private val sharedPrefs: SharedPreferences,
  private val appSettings: AppSettings,
  private val accountRepository: AccountRepository,
  private val postRepository: PostRepository,
) {
  @GuardedBy("mutex")
  private var kpnsAccountInfoCached: KPNSAccountInfoCached? = null
  private val mutex = Mutex()

  suspend fun kpnsAccountInfo(): KPNSAccountInfo {
    val now = System.currentTimeMillis()

    val kpnsAppInfoFromCache = getKpnsAccountInfoFromCache(now)
    if (kpnsAppInfoFromCache != null) {
      logcatDebug(TAG) { "kpnsAccountInfo() kpnsAppInfoFromCache != null" }
      return kpnsAppInfoFromCache
    }

    logcatDebug(TAG) { "kpnsAccountInfo() kpnsAppInfoFromCache == null" }

    val userId = sharedPrefs.getString(AppConstants.PrefKeys.USER_ID, null)
      ?.takeIf { userId -> isUserIdValid(userId) }
    val instanceAddress = sharedPrefs.getString(AppConstants.PrefKeys.INSTANCE_ADDRESS, null)
      ?.takeIf { instanceAddress -> instanceAddress.isNotNullNorBlank() }

    if (instanceAddress == null || userId == null) {
      logcatDebug(TAG) { "Bad userId: ${userId} or instanceAddress: ${instanceAddress}" }
      return KPNSAccountInfo.Error(errorMessage = "Bad userId: ${userId} or instanceAddress: ${instanceAddress}")
    }

    logcatDebug(TAG) { "InstanceAddress and UserId are OK" }

    val accountInfoResult = accountRepository.getAccountInfo(instanceAddress, userId)
    if (accountInfoResult.isFailure) {
      val exception = accountInfoResult.exceptionOrNull()!!
      logcatDebug(TAG) { "kpnsAccountInfo() getAccountInfo() error: ${exception.asLogIfImportantOrErrorMessage()}" }

      return KPNSAccountInfo.Error(exception.errorMessageOrClassName(userReadable = true))
    }

    val accountInfoResponse = accountInfoResult.getOrNull()
    if (accountInfoResponse == null) {
      return KPNSAccountInfo.Error("accountInfoResponse is null")
    }

    val validUntilMs = accountInfoResponse.validUntil
    if (validUntilMs == null) {
      return KPNSAccountInfo.Error("validUntil is null")
    }

    val isValid = accountInfoResponse.isValid
    logcatDebug(TAG) { "kpnsAccountInfo() getAccountInfo() success, isValid: ${isValid}, validUntilMs: ${validUntilMs}" }

    synchronized(this) {
      kpnsAccountInfoCached = KPNSAccountInfoCached(
        validUntilMs = validUntilMs,
        lastAccountValidationTimeMs = now
      )
    }

    return KPNSAccountInfo.Success(
      isAccountValid = isValid,
      validUntilMs = validUntilMs
    )
  }

  suspend fun startWatchingPost(postDescriptor: PostDescriptor): Result<Unit> {
    if (!isKpnsEnabled()) {
      return Result.success(Unit)
    }

    if (!isKpncAccountValid()) {
      logcatError(TAG) { "startWatchingPost() KPNS account is not valid" }
      return Result.failure(WatchPostError(postDescriptor, "KPNS account is not valid"))
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
      return Result.failure(WatchPostError(postDescriptor, "KPNS returned unsuccessful result"))
    }

    logcatDebug(TAG) { "startWatchingPost() success!" }
    return Result.success(Unit)
  }

  suspend fun stopWatchingPost(postDescriptor: PostDescriptor): Result<Unit> {
    if (!isKpnsEnabled()) {
      return Result.success(Unit)
    }

    if (!isKpncAccountValid()) {
      logcatError(TAG) { "stopWatchingPost() KPNS account is not valid" }
      return Result.failure(WatchPostError(postDescriptor, "KPNS account is not valid"))
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
      return Result.failure(WatchPostError(postDescriptor, "KPNS returned unsuccessful result"))
    }

    logcatDebug(TAG) { "stopWatchingPost() success!" }
    return Result.success(Unit)
  }

  suspend fun isKpncEnabledAndAccountIsValid(): Boolean {
    return isKpnsEnabled() && isKpncAccountValid()
  }

  suspend fun isKpnsEnabled(): Boolean {
    return appSettings.pushNotifications.read()
  }

  private suspend fun isKpncAccountValid(): Boolean {
    if (!isKpnsEnabled()) {
      return false
    }

    return (kpnsAccountInfo() as? KPNSAccountInfo.Success)?.isAccountValid == true
  }

  private suspend fun getKpnsAccountInfoFromCache(now: Long): KPNSAccountInfo? {
    return mutex.withLock {
      val kpncAccountInfoCachedLocal = kpnsAccountInfoCached
      if (kpncAccountInfoCachedLocal == null) {
        logcatDebug(TAG) { "kpnsAccountInfo() kpncAccountInfoCachedLocal is null" }
        return@withLock null
      }

      if (now >= kpncAccountInfoCachedLocal.validUntilMs) {
        logcatDebug(TAG) {
          "kpnsAccountInfo() kpncAccountInfoCachedLocal now >= kpncAccountInfoCachedLocal.validUntilMs " +
            "(${now} >= ${kpncAccountInfoCachedLocal.validUntilMs}, " +
            "delta: ${kpncAccountInfoCachedLocal.validUntilMs - now})"
        }

        return@withLock null
      }

      if (now - kpncAccountInfoCachedLocal.lastAccountValidationTimeMs > FIFTEEN_MINUTES) {
        logcatDebug(TAG) {
          "kpnsAccountInfo() now - kpncAccountInfoCachedLocal.lastAccountValidationTimeMs > FIFTEEN_MINUTES " +
            "(${now - kpncAccountInfoCachedLocal.lastAccountValidationTimeMs} >= ${FIFTEEN_MINUTES})"
        }

        return@withLock null
      }

      logcatDebug(TAG) { "kpnsAccountInfo() everything is OK" }

      return@withLock KPNSAccountInfo.Success(
        isAccountValid = true,
        validUntilMs = kpncAccountInfoCachedLocal.validUntilMs
      )
    }
  }

  open class KPNSError(message: String) : ClientException(message)

  class WatchPostError(postDescriptor: PostDescriptor, message: String)
    : KPNSError("Cannot start watching post \'${postDescriptor.asReadableString()}\': \'$message\'")

  companion object {
    private const val TAG = "KPNSHelper"

    private val FIFTEEN_MINUTES = TimeUnit.MINUTES.toMillis(15)
  }

}

private data class KPNSAccountInfoCached(
  val validUntilMs: Long,
  val lastAccountValidationTimeMs: Long
)

sealed class KPNSAccountInfo {
  fun errorAsReadableString(): String? {
    return when (this) {
      is Error -> "Error while trying to check KPNS status: \'${this.errorMessage}\'"
      is Success -> null
    }
  }

  data class Error(val errorMessage: String) : KPNSAccountInfo()
  data class Success(
    val isAccountValid: Boolean,
    val validUntilMs: Long
  ) : KPNSAccountInfo()
}