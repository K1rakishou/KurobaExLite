package com.github.k1rakishou.kurobaexlite.interactors

import android.content.Context
import android.os.Build
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.IKurobaOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendCall
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendConvertWithJsonAdapter
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.Request
import okhttp3.internal.closeQuietly
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class InstallMpvNativeLibrariesFromGithub(
  private val appContext: Context,
  private val moshi: Moshi,
  private val proxiedOkHttpClient: IKurobaOkHttpClient
) {
  private val lookupTag = "v${MPVLib.SUPPORTED_MPV_PLAYER_VERSION}"

  suspend fun execute(mpvSettings: MpvSettings): Result<Unit> {
    return Result.Try { withContext(Dispatchers.IO) { executeInternal(mpvSettings) } }
  }

  private suspend fun executeInternal(mpvSettings: MpvSettings) {
    val abi = Build.SUPPORTED_ABIS.firstOrNull { abi -> abi.lowercase() in LIB_ABIS }
    logcat(TAG) { "Supported abis: \'${Build.SUPPORTED_ABIS.joinToString()}\', selected abi: \'${abi}\'" }

    if (abi == null) {
      throw MpvInstallLibsFromGithubException("No suitable ABI found: " +
        "expected one of: \'${LIB_ABIS.joinToString()}\', " +
        "got: \'${Build.SUPPORTED_ABIS.joinToString()}\'")
    }

    val request = Request.Builder()
      .get()
      .url(KUROBAEX_MPV_LIBS_RELEASES_ENDPOINT)
      .build()

    val githubReleaseResponsesListType = Types.newParameterizedType(List::class.java, GithubReleaseResponse::class.java)
    val adapter = moshi.adapter<List<GithubReleaseResponse>>(githubReleaseResponsesListType)
    val githubReleases = proxiedOkHttpClient.okHttpClient().suspendConvertWithJsonAdapter(request, adapter)
      .unwrap()

    if (githubReleases == null) {
      throw MpvInstallLibsFromGithubException("Failed to convert json to GithubReleaseResponse")
    }

    val releaseForThisApp = githubReleases
      .firstOrNull { githubReleaseResponse -> githubReleaseResponse.tagName.equals(lookupTag, ignoreCase = true) }

    if (releaseForThisApp == null) {
      throw MpvInstallLibsFromGithubException("Failed to find libraries for \'${lookupTag}\' tag")
    }

    val githubAssetForThisApp = releaseForThisApp.assets.firstOrNull { githubAsset ->
      val archiveName = githubAsset.downloadUrl
        .removePrefix(KUROBAEX_MPV_LIBS_RELEASES_ENDPOINT)
        .removePrefix(lookupTag)

      if (archiveName.contains(abi, ignoreCase = true)) {
        return@firstOrNull true
      }

      return@firstOrNull false
    }

    if (githubAssetForThisApp == null) {
      val downloadUrls = releaseForThisApp.assets
        .joinToString(transform = { githubAsset -> githubAsset.downloadUrl })

      throw MpvInstallLibsFromGithubException("Failed to find download url for ABI ${abi}, downloadUrls: \'${downloadUrls}\'")
    }

    val downloadUrl = githubAssetForThisApp.downloadUrl
    logcat(TAG) { "Downloading \'${downloadUrl}\'" }

    val downloadRequest = Request.Builder()
      .get()
      .url(downloadUrl)
      .build()

    val response = proxiedOkHttpClient.okHttpClient()
      .suspendCall(downloadRequest)
      .unwrap()

    if (!response.isSuccessful) {
      throw BadStatusResponseException(response.code)
    }

    val body = response.body
      ?: throw EmptyBodyResponseException()

    val mpvLibsZipArchiveFile = File(appContext.cacheDir, "mpv_libs.zip")
    logcat(TAG) { "mpvLibsZipArchiveFile: \'${mpvLibsZipArchiveFile.absolutePath}\'" }

    try {
      runInterruptible {
        if (mpvLibsZipArchiveFile.exists()) {
          mpvLibsZipArchiveFile.delete()
        }

        if (!mpvLibsZipArchiveFile.createNewFile()) {
          throw MpvInstallLibsFromGithubException("Failed to create mpv libs output file on disk")
        }

        body.source().inputStream().use { inputStream ->
          mpvLibsZipArchiveFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
          }
        }

        logcat(TAG) { "Deleting old lib files" }

        mpvSettings.mpvNativeLibsDir.listFiles()
          ?.forEach { libFile ->
            logcat(TAG) { "Deleting ${libFile.absolutePath}" }
            libFile.delete()
          }

        logcat(TAG) { "Extracting archived libs into \'${mpvSettings.mpvNativeLibsDir}\'" }
        extractArchiveAndMoveToLibsDirectory(mpvLibsZipArchiveFile, mpvSettings.mpvNativeLibsDir, mpvSettings.mpvCertsDir)
        logcat(TAG) { "Done" }
      }
    } catch (error: Throwable) {
      logcatError(TAG) { "Got error: \'${error.errorMessageOrClassName()}\' deleting everything in \'${mpvSettings.mpvNativeLibsDir}\'" }
      mpvSettings.mpvNativeLibsDir.listFiles()?.forEach { file -> file.delete() }

      throw error
    } finally {
      mpvLibsZipArchiveFile.delete()
    }

    logcat(TAG) { "All done" }
  }

  private fun extractArchiveAndMoveToLibsDirectory(
    mpvLibsFile: File,
    mpvNativeLibsDir: File,
    mpvCertsDir: File
  ) {
    if (!mpvNativeLibsDir.exists()) {
      check(mpvNativeLibsDir.mkdirs()) { "Failed to create '${mpvNativeLibsDir.absolutePath}'" }
    }

    if (!mpvCertsDir.exists()) {
      check(mpvCertsDir.mkdirs()) { "Failed to create '${mpvCertsDir.absolutePath}'" }
    }

    mpvLibsFile.inputStream().use { inputStream ->
      val zipInputStream = ZipInputStream(inputStream)
      var zipEntry: ZipEntry? = null
      var zipMalformed = true

      try {
        while (true) {
          zipEntry = zipInputStream.nextEntry
            ?: break

          if (zipEntry.isDirectory) {
            logcat(TAG) { "Skipping directory '${zipEntry.name}'" }
            continue
          }

          val fileName = zipEntry.name.split(delimiters = arrayOf("/", "\\")).lastOrNull()
          if (fileName.isNullOrBlank()) {
            logcatError(TAG) { "Bad file name: '${fileName}', zipEntry.name: '${zipEntry.name}'" }
            continue
          }

          logcat(TAG) { "fileName: \'${fileName}\'" }

          if (fileName.endsWith(".so")) {
            val outputMpvLibFile = File(mpvNativeLibsDir, fileName)

            logcat(TAG) { "Moving \'${fileName}\' from archive to \'${outputMpvLibFile.absolutePath}\' file" }

            outputMpvLibFile.outputStream().use { outputStream ->
              zipInputStream.copyTo(outputStream)
            }

            logcat(TAG) { "Done" }
          } else if (fileName == "cacert.pem") {
            val outputMpvCertFile = File(mpvCertsDir, fileName)

            logcat(TAG) { "Moving \'${fileName}\' from archive to \'${outputMpvCertFile.absolutePath}\' file" }

            outputMpvCertFile.outputStream().use { outputStream ->
              zipInputStream.copyTo(outputStream)
            }

            logcat(TAG) { "Done" }
          } else {
            logcat(TAG) { "'${fileName}' is an unknown file, skipping" }
          }

          zipInputStream.closeEntry()
          zipMalformed = false
        }
      } finally {
        zipInputStream.closeQuietly()
      }

      if (zipMalformed) {
        throw IOException("Failed to open mpv libs zip archive: '${mpvLibsFile.absolutePath}'")
      }
    }
  }

  class MpvInstallLibsFromGithubException(message: String) : Exception(message)

  @JsonClass(generateAdapter = true)
  data class GithubReleaseResponse(
    @Json(name = "tag_name")
    val tagName: String,
    @Json(name = "assets")
    val assets: List<GithubAsset>
  )

  @JsonClass(generateAdapter = true)
  data class GithubAsset(
    @Json(name = "name")
    val name: String,
    @Json(name = "browser_download_url")
    val downloadUrl: String
  )

  companion object {
    private const val TAG = "InstallMpvNativeLibrariesFromGithubUseCase"
    private const val KUROBAEX_MPV_LIBS_RELEASES_ENDPOINT = "https://api.github.com/repos/K1rakishou/KurobaEx-mpv-libs/releases"

    private val LIB_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
      .map { it.lowercase() }
  }

}