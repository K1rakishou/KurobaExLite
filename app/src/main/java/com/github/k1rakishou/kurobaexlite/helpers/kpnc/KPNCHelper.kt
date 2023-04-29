package com.github.k1rakishou.kurobaexlite.helpers.kpnc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatDebug
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.sendOrderedBroadcastSuspend
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

class KPNCHelper(
  private val appContext: Context,
  private val moshi: Moshi,
  private val siteManager: SiteManager
) {
  @Volatile private var kpncAppInfo: KPNCAppInfo? = null

  suspend fun kpncAppInfo(): KPNCAppInfo {
    if (kpncAppInfo != null) {
      return kpncAppInfo!!
    }

    logcatDebug(TAG) { "kpncAppInfo()" }
    val intent = Intent(ACTION_GET_INFO)

    val resultBundle = sendBroadcastInternal(appContext, intent)
    if (resultBundle == null) {
      logcatError(TAG) { "kpncAppInfo() resultBundle == null" }
      return KPNCAppInfo.NotInstalled
    }

    val kpncInfoJson = resultBundle.getString(ACTION_GET_INFO_RESULT)
    if (kpncInfoJson.isNullOrEmpty()) {
      logcatError(TAG) { "kpncAppInfo() kpncInfoJson == null" }
      return KPNCAppInfo.NotInstalled
    }

    val kpncInfoResult = moshi.adapter<KPNCInfoResult>(KPNCInfoResult::class.java).fromJson(kpncInfoJson)
    if (kpncInfoResult == null) {
      logcatError(TAG) { "kpncAppInfo() kpncInfoJson conversion failed" }
      return KPNCAppInfo.NotInstalled
    }

    if (kpncInfoResult.error != null) {
      logcatError(TAG) { "kpncAppInfo() Error while trying to execute KPNC request: ${kpncInfoResult.error}" }
      return KPNCAppInfo.Error(kpncInfoResult.error)
    }

    val kpncInfo = kpncInfoResult.data!!
    if (kpncInfo.appApiVersion != API_VERSION) {
      logcatError(TAG) { "kpncAppInfo() appApiVersion (${kpncInfo.appApiVersion}) != API_VERSION ($API_VERSION)" }
      return KPNCAppInfo.InstalledVersionMismatch(
        expected = API_VERSION,
        actual = kpncInfo.appApiVersion
      )
    }

    if (kpncInfo.isAccountValid) {
      kpncAppInfo = KPNCAppInfo.Installed(isAccountValid = true)
    }

    logcatDebug(TAG) { "kpncAppInfo() success, isAccountValid = ${kpncInfo.isAccountValid}" }
    return kpncAppInfo!!
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

    val intent = Intent(ACTION_START_WATCHING_POST)
    intent.putExtra(POST_URL, postUrl)

    val resultBundle = sendBroadcastInternal(appContext, intent)
    if (resultBundle == null) {
      logcatError(TAG) { "startWatchingPost() resultBundle == null" }
      return Result.failure(WatchPostError(postDescriptor, "Error communicating with KPNC"))
    }

    val watchPostResultJson = resultBundle.getString(ACTION_START_WATCHING_POST_RESULT, null)
    if (watchPostResultJson.isNullOrEmpty()) {
      logcatError(TAG) { "startWatchingPost() watchPostResultJson == null" }
      return Result.failure(WatchPostError(postDescriptor, "KPNC didn't send a response"))
    }

    val watchPostResult = moshi
      .adapter<WatchPostResult>(WatchPostResult::class.java)
      .fromJson(watchPostResultJson)

    if (watchPostResult == null) {
      logcatError(TAG) { "startWatchingPost() watchPostResult == null, watchPostResultJson: \'$watchPostResultJson\'" }
      return Result.failure(WatchPostError(postDescriptor, "WatchPostResult json conversion failed"))
    }

    if (watchPostResult.error != null) {
      logcatError(TAG) { "startWatchingPost() Error while trying to execute KPNC request: ${watchPostResult.error}" }
      return Result.failure(WatchPostError(postDescriptor, watchPostResult.error))
    }

    val success = watchPostResult.data?.success == true
    if (!success) {
      logcatError(TAG) { "startWatchingPost() watchPostResult.data.success == false" }
      return Result.failure(WatchPostError(postDescriptor, "KPNC returned unsuccessful result"))
    }

    logcatDebug(TAG) { "startWatchingPost() success!" }
    return Result.success(Unit)
  }

  suspend fun stopWatchingPost(postDescriptor: PostDescriptor): Result<Unit> {
    logcatDebug(TAG) { "stopWatchingPost(${postDescriptor})" }

    // TODO:
    return Result.success(Unit)
  }

  suspend fun isKpncEnabledAndAccountIsValid(): Boolean {
    return isKpncEnabled() && isKpncAccountValid()
  }

  suspend fun isKpncEnabled(): Boolean {
//    val kpncInfo = kpncAppInfo()
//    if (kpncInfo !is KPNCAppInfo.Installed) {
//      return false
//    }
//
//    return true
    // TODO: Check whether a setting is enabled or not here
    return true
  }

  private suspend fun isKpncAccountValid(): Boolean {
    if (!isKpncEnabled()) {
      return false
    }

    return (kpncAppInfo() as? KPNCAppInfo.Installed)?.isAccountValid == true
  }

  private suspend fun sendBroadcastInternal(context: Context, intent: Intent): Bundle? {
    val broadcastReceiversInfo = context.packageManager.queryBroadcastReceivers(intent, 0)
    logcatDebug(TAG) { "broadcastReceiversInfo=${broadcastReceiversInfo.size}" }

    val broadcastReceiver = broadcastReceiversInfo.firstOrNull()
      ?: return null

    logcatDebug(TAG) { "Using packageName: ${broadcastReceiver.activityInfo.packageName}, "
      "name: ${broadcastReceiver.activityInfo.name}" }

    intent.component = ComponentName(
      broadcastReceiver.activityInfo.packageName,
      broadcastReceiver.activityInfo.name
    )

    return sendOrderedBroadcastSuspend(context, intent)
  }

  @JsonClass(generateAdapter = true)
  data class KPNCInfoResult(
    override val data: KPNCInfoJson? = null,
    override val error: String? = null
  ) : GenericResult<KPNCInfoJson>()

  @JsonClass(generateAdapter = true)
  data class KPNCInfoJson(
    @Json(name = "app_api_version")
    val appApiVersion: Int,
    @Json(name = "is_account_valid")
    val isAccountValid: Boolean
  )

  @JsonClass(generateAdapter = true)
  data class WatchPostResult(
    override val data: DefaultSuccessResult? = null,
    override val error: String? = null
  ) : GenericResult<DefaultSuccessResult>()

  abstract class GenericResult<T> {
    abstract val data: T?
    abstract val error: String?
  }

  @JsonClass(generateAdapter = true)
  data class DefaultSuccessResult(
    val success: Boolean = true
  )

  open class KPNCError(message: String) : ClientException(message)

  class WatchPostError(postDescriptor: PostDescriptor, message: String)
    : KPNCError("Cannot start watching post \'${postDescriptor.asReadableString()}\': \'$message\'")

  companion object {
    private const val TAG = "KPNCHelper"

    private const val API_VERSION = 1
    private const val PACKAGE = "com.github.k1rakishou.kpnc"
    
    private const val ACTION_GET_INFO = "$PACKAGE.get_info"
    private const val ACTION_GET_INFO_RESULT = "$PACKAGE.get_info_result"

    private const val ACTION_START_WATCHING_POST = "$PACKAGE.start_watching_post"
    private const val POST_URL = "post_url"
    private const val ACTION_START_WATCHING_POST_RESULT = "$PACKAGE.start_watching_post_result"
  }

}

sealed class KPNCAppInfo {
  fun errorAsReadableString(): String? {
    return when (this) {
      NotInstalled -> "KPNC is not installed"
      is Error -> "Error while trying to check KPNC status: \'${this.errorMessage}\'"
      is InstalledVersionMismatch -> "KPNC is installed but wrong version. Expected: ${this.expected}, actual: ${this.actual}"
      is Installed -> null
    }
  }

  object NotInstalled : KPNCAppInfo()
  data class Error(val errorMessage: String) : KPNCAppInfo()
  data class InstalledVersionMismatch(val expected: Int, val actual: Int) : KPNCAppInfo()
  data class Installed(val isAccountValid: Boolean) : KPNCAppInfo()
}