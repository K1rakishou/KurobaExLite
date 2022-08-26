package com.github.k1rakishou.kurobaexlite.helpers.picker

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.activity.ComponentActivity
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.features.reply.AttachedMedia
import com.github.k1rakishou.kurobaexlite.helpers.AndroidHelpers
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.executors.SerializedCoroutineExecutor
import com.github.k1rakishou.kurobaexlite.helpers.resource.AppResources
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.BackgroundUtils
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.util.exceptionOrThrow
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.logcat

class LocalFilePicker(
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val appSettings: AppSettings,
  private val androidHelpers: AndroidHelpers,
  private val appResources: AppResources,
  private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AbstractFilePicker(appContext) {
  private var componentActivity: ComponentActivity? = null
  private val selectedFilePickerBroadcastReceiver = SelectedFilePickerBroadcastReceiver()
  private val requestCodeCounter = AtomicInteger(0)
  private val activeRequests = ConcurrentHashMap<Int, EnqueuedRequest>()
  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(
    scope = appScope,
    dispatcher = coroutineDispatcher
  )

  fun attachActivity(componentActivity: ComponentActivity) {
    this.componentActivity = componentActivity
  }

  fun detachActivity() {
    this.componentActivity = null
  }

  suspend fun pickFile(
    chanDescriptor: ChanDescriptor,
    allowMultiSelection: Boolean
  ): Result<List<AttachedMedia>> {
    return runCatching {
      return@runCatching pickFileInternal(
        chanDescriptor = chanDescriptor,
        allowMultiSelection = allowMultiSelection
      )
    }
  }

  private suspend fun pickFileInternal(
    chanDescriptor: ChanDescriptor,
    allowMultiSelection: Boolean
  ): List<AttachedMedia> {
//    if (filePickerInput.clearLastRememberedFilePicker) {
//      PersistableChanState.lastRememberedFilePicker.set("")
//    }

    val attachedActivity = componentActivity
    if (attachedActivity == null) {
      throw PickFileError("Activity is not set")
    }

    val intents = collectIntents(allowMultiSelection = allowMultiSelection)
    if (intents.isEmpty()) {
      throw PickFileError("No file pickers found in the system")
    }

    val completableDeferred = CompletableDeferred<List<AttachedMedia>>()

    val newRequestCode = requestCodeCounter.getAndIncrement()
    check(!activeRequests.containsKey(newRequestCode)) { "Already contains newRequestCode=$newRequestCode" }

    val newRequest = EnqueuedRequest(
      newRequestCode,
      chanDescriptor,
      completableDeferred
    )
    activeRequests[newRequestCode] = newRequest

    check(intents.isNotEmpty()) { "intents is empty!" }
    runIntentChooser(attachedActivity, intents, newRequest.requestCode)

    val pickFileResult = try {
      runCatching { completableDeferred.await() }
    } finally {
      activeRequests.remove(newRequest.requestCode)
    }

    if (pickFileResult.isFailure) {
      val error = pickFileResult.exceptionOrThrow()
      if (error is CancellationException) {
        return emptyList()
      }

      throw error
    }

    return pickFileResult.getOrThrow()
  }

  fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    BackgroundUtils.ensureMainThread()

    serializedCoroutineExecutor.post {
      try {
        onActivityResultInternal(requestCode, resultCode, data)
      } catch (error: Throwable) {
        finishWithError(requestCode, error)
      }
    }
  }

  private suspend fun onActivityResultInternal(requestCode: Int, resultCode: Int, data: Intent?) {
    BackgroundUtils.ensureBackgroundThread()

    val enqueuedRequest = activeRequests[requestCode]
    if (enqueuedRequest == null) {
      return
    }

    val attachedActivity = componentActivity
    if (attachedActivity == null) {
      return finishWithError(requestCode, PickFileError("Activity is not set"))
    }

    if (resultCode != Activity.RESULT_OK) {
      if (resultCode == Activity.RESULT_CANCELED) {
        finishWithError(requestCode, PickFileError("Canceled"))
        return
      }

      finishWithError(requestCode, PickFileError("Bad result code: ${resultCode}"))
      return
    }

    if (data == null) {
      finishWithError(requestCode, PickFileError("No data returned"))
      return
    }

    val uris = extractUris(data)
    if (uris.isEmpty()) {
      finishWithError(requestCode, PickFileError("Failed to extract uris"))
      return
    }

    val copyResults = uris.map { uri ->
      copyExternalFileToReplyFileStorage(
        chanDescriptor = enqueuedRequest.chanDescriptor,
        externalFileUri = uri,
      )
    }

    val allFailed = copyResults.all { result -> result.isFailure }
    if (allFailed) {
      val firstErrorResult = copyResults.first { result -> result.isFailure }
      finishWithError(requestCode, firstErrorResult.exceptionOrThrow())
      return
    }

    val pickedFiles = copyResults
      .mapNotNull { result -> result.getOrNull() }

    if (pickedFiles.isEmpty()) {
      finishWithError(requestCode, PickFileError("Failed to pick anything"))
      return
    }

    finishWithResult(requestCode, pickedFiles)
  }

  private fun finishWithResult(requestCode: Int, attachedMediaList: List<AttachedMedia>) {
    logcat(TAG) {
      "finishWithResult success, requestCode=$requestCode, " +
        "attachedMedias=${attachedMediaList.size}"
    }

    activeRequests[requestCode]?.completableDeferred?.complete(attachedMediaList)
  }

  private fun finishWithError(requestCode: Int, error: Throwable) {
    logcatError(TAG) {
      "finishWithError success, requestCode=$requestCode, " +
        "error=${error.errorMessageOrClassName()}"
    }

    activeRequests[requestCode]?.completableDeferred?.completeExceptionally(error)
  }

  private fun extractUris(intent: Intent): List<Uri> {
    if (intent.data != null) {
      return listOf(intent.data!!)
    }

    val clipData = intent.clipData
    if (clipData != null && clipData.itemCount > 0) {
      return (0 until clipData.itemCount)
        .map { index -> clipData.getItemAt(index).uri }
    }

    return emptyList()
  }

  private suspend fun collectIntents(allowMultiSelection: Boolean): List<Intent> {
    val pm = appContext.packageManager
    val intent = Intent(Intent.ACTION_GET_CONTENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.type = "*/*"

    val resolveInfos = pm.queryIntentActivities(intent, 0)
    val intents: MutableList<Intent> = ArrayList(resolveInfos.size)

    val lastRememberedFilePicker = appSettings.lastRememberedFilePicker.read()
    if (lastRememberedFilePicker.isNotEmpty()) {
      val lastRememberedFilePickerInfo = resolveInfos.firstOrNull { resolveInfo ->
        resolveInfo.activityInfo.packageName == lastRememberedFilePicker
      }

      if (lastRememberedFilePickerInfo != null) {
        val newIntent = Intent(Intent.ACTION_GET_CONTENT)
        newIntent.addCategory(Intent.CATEGORY_OPENABLE)
        newIntent.setPackage(lastRememberedFilePickerInfo.activityInfo.packageName)
        newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        newIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiSelection)
        newIntent.type = "*/*"

        return listOf(newIntent)
      }
    }

    for (info in resolveInfos) {
      val newIntent = Intent(Intent.ACTION_GET_CONTENT)
      newIntent.addCategory(Intent.CATEGORY_OPENABLE)
      newIntent.setPackage(info.activityInfo.packageName)
      newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      newIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiSelection)
      newIntent.type = "*/*"

      intents.add(newIntent)
    }

    return intents
  }

  private fun runIntentChooser(activity: ComponentActivity, intents: List<Intent>, requestCode: Int) {
    check(intents.isNotEmpty()) { "intents is empty!" }

    if (intents.size == 1) {
      activity.startActivityForResult(intents[0], requestCode)
      return
    }

    val chooser = if (androidHelpers.isAndroidL_MR1()) {
      val receiverIntent = Intent(
        activity,
        SelectedFilePickerBroadcastReceiver::class.java
      )

      val pendingIntent = PendingIntent.getBroadcast(
        activity,
        AppConstants.RequestCodes.LOCAL_FILE_PICKER_LAST_SELECTION_REQUEST_CODE,
        receiverIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
      )

      activity.registerReceiver(
        selectedFilePickerBroadcastReceiver,
        IntentFilter(Intent.ACTION_GET_CONTENT)
      )

      Intent.createChooser(
        intents.last(),
        appResources.string(R.string.image_pick_delegate_select_file_picker),
        pendingIntent.intentSender
      )
    } else {
      Intent.createChooser(
        intents.last(),
        appResources.string(R.string.image_pick_delegate_select_file_picker)
      )
    }

    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toTypedArray())
    activity.startActivityForResult(chooser, requestCode)
  }

  private fun copyExternalFileToReplyFileStorage(
    chanDescriptor: ChanDescriptor,
    externalFileUri: Uri,
  ): Result<AttachedMedia> {
    BackgroundUtils.ensureBackgroundThread()

    return runCatching {
      if (!attachedMediaDir.exists()) {
        check(attachedMediaDir.mkdirs()) { "Failed to create \'${attachedMediaDir.path}\' directory" }
      }

      val extension = externalFileUri.lastPathSegment
        ?.substringAfterLast('.')
        ?.takeIf { extension -> extension.length <= 8 }

      val replyFile = createAttachMediaFile(chanDescriptor, attachedMediaDir, extension)
      val originalFileName = tryExtractFileNameOrDefault(externalFileUri, appContext)

      if (replyFile == null) {
        throw IOException("Failed to get attach file")
      }

      try {
        copyExternalFileIntoReplyFile(appContext, externalFileUri, replyFile)
      } catch (error: Throwable) {
        replyFile.delete()
        throw error
      }

      return@runCatching AttachedMedia(
        path = replyFile.absolutePath,
        fileName = originalFileName
      )
    }
  }

  suspend fun cleanup() {
    withContext(coroutineDispatcher) {
      val filesInDirectory = attachedMediaDir.listFiles() ?: emptyArray()

      filesInDirectory.forEach { file ->
        if (file.isFile) {
          logcatError(TAG) { "Found a file (${file.path}) where only directories are supposed to be" }
          file.delete()
          return@forEach
        }

        file.deleteRecursively()
      }

      logcat(TAG) { "cleanup() removed ${filesInDirectory.size} directories with files" }
    }
  }

  private data class EnqueuedRequest(
    val requestCode: Int,
    val chanDescriptor: ChanDescriptor,
    val completableDeferred: CompletableDeferred<List<AttachedMedia>>
  )

  companion object {
    private const val TAG = "LocalFilePicker"
  }

}