package com.github.k1rakishou.kurobaexlite.helpers.post_bind

import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPart
import com.github.k1rakishou.kurobaexlite.helpers.post_bind.processors.Chan4MathTagProcessor
import com.github.k1rakishou.kurobaexlite.helpers.post_bind.processors.IPostProcessor
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatVerbose
import com.github.k1rakishou.kurobaexlite.helpers.util.parallelForEach
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class PostBindProcessorCoordinator(
  private val chan4MathTagProcessor: Chan4MathTagProcessor,
  private val appScope: CoroutineScope
) {
  private val activeBoundPostJobs = mutableMapOf<PostDescriptor, Job>()

  private val processors: List<IPostProcessor> by lazy {
    listOf(
      chan4MathTagProcessor
    )
  }

  private val _pendingPostsForReparsingFlow = MutableSharedFlow<PostDescriptor>(extraBufferCapacity = Channel.UNLIMITED)
  val pendingPostsForReparsingFlow: SharedFlow<PostDescriptor>
    get() = _pendingPostsForReparsingFlow.asSharedFlow()

  private val _postInlinedContentProcessingEventFlow = MutableSharedFlow<InlinedContentProcessingEvent>(extraBufferCapacity = Channel.UNLIMITED)
  val postInlinedContentProcessingEventFlow: SharedFlow<InlinedContentProcessingEvent>
    get() = _postInlinedContentProcessingEventFlow.asSharedFlow()

  suspend fun removeCached(chanDescriptor: ChanDescriptor) {
    processors.forEach { postProcessor -> postProcessor.removeCached(chanDescriptor) }
  }

  suspend fun applyData(
    textPart: TextPart,
    postDescriptor: PostDescriptor
  ): IPostProcessor.AppliedDataResult {
    var localTextPart = textPart
    val inlinedContentList = mutableListOf<IPostProcessor.InlinedContent>()

    for (postProcessor in processors) {
      val appliedDataResult = postProcessor.applyData(localTextPart, postDescriptor)
        ?: continue

      inlinedContentList.addAll(appliedDataResult.inlinedContentList)
      localTextPart = appliedDataResult.textPart
    }

    return IPostProcessor.AppliedDataResult(
      textPart = textPart,
      inlinedContentList = inlinedContentList
    )
  }

  fun forceLoadInlinedContent(
    isCatalogMode: Boolean,
    postDescriptor: PostDescriptor
  ) {
    logcatVerbose(TAG) { "forceLoadInlinedContent(${isCatalogMode}, ${postDescriptor})" }

    onPostUnbind(isCatalogMode, postDescriptor)
    onPostBind(isCatalogMode, postDescriptor)
  }

  fun onPostBind(
    isCatalogMode: Boolean,
    postDescriptor: PostDescriptor
  ) {
    if (activeBoundPostJobs.containsKey(postDescriptor)) {
      return
    }

    activeBoundPostJobs[postDescriptor] = appScope.launch(Dispatchers.Default) {
      val cached = processors.all { postProcessor -> postProcessor.isCached(postDescriptor) }
      if (!cached) {
        delay(1000L)
      }

      ensureActive()

      onPostBindInternal(
        isCatalogMode = isCatalogMode,
        postDescriptor = postDescriptor
      )
    }
  }

  fun onPostUnbind(isCatalogMode: Boolean, postDescriptor: PostDescriptor) {
    _postInlinedContentProcessingEventFlow.tryEmit(InlinedContentProcessingEvent.Ended(postDescriptor))
    activeBoundPostJobs.remove(postDescriptor)?.cancel()
  }

  private suspend fun onPostBindInternal(
    isCatalogMode: Boolean,
    postDescriptor: PostDescriptor
  ) {
    val listenersNotified = AtomicBoolean(false)

    val results = parallelForEach(
      dataList = processors,
      parallelization = processors.size,
      dispatcher = Dispatchers.Default
    ) { processor ->
      processor.process(
        isCatalogMode = isCatalogMode,
        postDescriptor = postDescriptor,
        onFoundContentToProcess = {
          if (!listenersNotified.compareAndSet(false, true)) {
            return@process
          }

          _postInlinedContentProcessingEventFlow.tryEmit(InlinedContentProcessingEvent.Started(postDescriptor))
        },
        onEndedProcessing = {
          _postInlinedContentProcessingEventFlow.tryEmit(InlinedContentProcessingEvent.Ended(postDescriptor))
        }
      )
    }

    val postNeedsToBeReparsed = results.any { success -> success }
    if (!postNeedsToBeReparsed) {
      return
    }

    _pendingPostsForReparsingFlow.emit(postDescriptor)
  }

  sealed class InlinedContentProcessingEvent {
    abstract val postDescriptor: PostDescriptor

    data class Started(override val postDescriptor: PostDescriptor) : InlinedContentProcessingEvent()
    data class Ended(override val postDescriptor: PostDescriptor) : InlinedContentProcessingEvent()
  }

  companion object {
    private const val TAG = "PostBindProcessor"
  }

}