package com.github.k1rakishou.kurobaexlite.helpers.filtering

import androidx.annotation.VisibleForTesting
import com.github.k1rakishou.kurobaexlite.helpers.AppConstants
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.parallelForEachOrdered
import com.github.k1rakishou.kurobaexlite.helpers.util.toHashSetOfKeysBy
import com.github.k1rakishou.kurobaexlite.helpers.util.unreachable
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.IPostHideRepository
import com.github.k1rakishou.kurobaexlite.model.repository.IPostReplyChainRepository
import com.github.k1rakishou.kurobaexlite.model.repository.ParsedPostDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.logcat
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class PostFilterHelper(
  private val postHideRepository: IPostHideRepository,
  private val postReplyChainRepository: IPostReplyChainRepository
) {

  @OptIn(ExperimentalTime::class)
  suspend fun filterPosts(chanDescriptor: ChanDescriptor, posts: List<PostCellData>): List<PostCellData> {
    logcat(TAG) { "filterPosts() processing ${posts.size} posts..." }
    val (resultPosts, duration) = measureTimedValue { processPostsInternal(chanDescriptor, posts) }
    logcat(TAG) { "filterPosts() processing ${posts.size} posts... done, took ${duration}" }

    return resultPosts
  }

  @VisibleForTesting
  suspend fun processPostsInternal(chanDescriptor: ChanDescriptor, posts: List<PostCellData>): List<PostCellData> {
    if (posts.isEmpty()) {
      return posts
    }

    val mutex = Mutex()

    val allPostDescriptors = posts.toHashSetOfKeysBy { it.postDescriptor }
    val postHides = postHideRepository.postHidesForChanDescriptor(chanDescriptor).toMutableMap()
    val processingCatalog = chanDescriptor is CatalogDescriptor

    val toHide = mutableListOf<ChanPostHide>()
    val toUnhide = mutableSetOf<PostDescriptor>()

    val filteredPosts = parallelForEachOrdered(
      dataList = posts,
      parallelization = AppConstants.coresCount,
      dispatcher = Dispatchers.Default
    ) { postCellData ->
      val visitedPosts = mutableSetWithCap<PostDescriptor>(posts.size)

      val postHideResult = processSinglePost(
        processingCatalog = processingCatalog,
        postCellData = postCellData,
        allPostDescriptors = allPostDescriptors,
        getPostHide = { postDescriptor -> mutex.withLock { postHides[postDescriptor] } },
        tryVisitPost = { postDescriptorToVisit -> visitedPosts.add(postDescriptorToVisit) }
      )

      when (postHideResult) {
        null -> return@parallelForEachOrdered null
        PostHideResult.Unchanged -> return@parallelForEachOrdered postCellData
        is PostHideResult.Hide -> {
          mutex.withLock {
            postHides[postHideResult.chanPostHide.postDescriptor] = postHideResult.chanPostHide
            toHide += postHideResult.chanPostHide
          }

          return@parallelForEachOrdered postCellData.copy(postHideUi = postHideResult.chanPostHide.toPostHideUi())
        }
        PostHideResult.Unhide -> {
          mutex.withLock {
            postHides.remove(postCellData.postDescriptor)
            toUnhide += postCellData.postDescriptor
          }

          return@parallelForEachOrdered postCellData.copy(postHideUi = null)
        }
      }
    }

    if (toHide.isNotEmpty()) {
      postHideRepository.createOrUpdate(chanDescriptor, toHide)
    }

    if (toUnhide.isNotEmpty()) {
      postHideRepository.update(toUnhide) { chanPostHide -> chanPostHide.copy(manuallyUnhidden = true) }
    }

    return filteredPosts
  }

  private suspend fun processSinglePost(
    processingCatalog: Boolean,
    postCellData: PostCellData,
    allPostDescriptors: Set<PostDescriptor>,
    getPostHide: suspend (PostDescriptor) -> ChanPostHide?,
    tryVisitPost: (PostDescriptor) -> Boolean
  ): PostHideResult? {
    if (!tryVisitPost(postCellData.postDescriptor)) {
      // Post already processed
      return null
    }

    val postDescriptor = postCellData.postDescriptor
    val chanPostHide = getPostHide(postDescriptor)

    if (canHidePost(processingCatalog, postCellData, chanPostHide)) {
      val newChanPostHide = ChanPostHide(
        postDescriptor = postDescriptor,
        applyToReplies = chanPostHide?.applyToReplies ?: false,
        manuallyUnhidden = false,
        reason = postHideReason(processingCatalog, postDescriptor, chanPostHide)
      )

      return PostHideResult.Hide(newChanPostHide)
    }

    if (postCellData.postHideUi != null && chanPostHide != null && chanPostHide.manuallyUnhidden) {
      return PostHideResult.Unhide
    }

    if (!processingCatalog) {
      val repliesToPosts = postReplyChainRepository.getRepliesTo(postDescriptor)

      for (postDescriptorReplyTo in repliesToPosts) {
        if (!tryVisitPost(postDescriptorReplyTo)) {
          // Post already processed
          continue
        }

        val parentChanPostHide = findParentNonNullChanPostHide(
          postDescriptor = postDescriptorReplyTo,
          allPostDescriptors = allPostDescriptors,
          getPostHide = getPostHide,
          visitedPosts = mutableSetOf()
        )

        if (parentChanPostHide == null || !parentChanPostHide.applyToReplies) {
          continue
        }

        if (canHidePost(false, postCellData, parentChanPostHide)) {
          @Suppress("KotlinConstantConditions") val newChanPostHide = ChanPostHide(
            postDescriptor = postDescriptorReplyTo,
            applyToReplies = true,
            manuallyUnhidden = parentChanPostHide.manuallyUnhidden,
            reason = postHideReason(processingCatalog, postDescriptorReplyTo, parentChanPostHide)
          )

          return PostHideResult.Hide(newChanPostHide)
        }
      }
    }

    if (postCellData.postHideUi != null) {
      return PostHideResult.Unhide
    }

    return PostHideResult.Unchanged
  }

  private suspend fun findParentNonNullChanPostHide(
    postDescriptor: PostDescriptor,
    allPostDescriptors: Set<PostDescriptor>,
    getPostHide: suspend (PostDescriptor) -> ChanPostHide?,
    visitedPosts: MutableSet<PostDescriptor>
  ): ChanPostHide? {
    val chanPostHide = getPostHide(postDescriptor)
    if (chanPostHide != null) {
      return chanPostHide
    }

    if (!allPostDescriptors.contains(postDescriptor)) {
      return null
    }

    visitedPosts += postDescriptor
    val repliesTo = postReplyChainRepository.getRepliesTo(postDescriptor)

    for (replyTo in repliesTo) {
      if (visitedPosts.contains(replyTo)) {
        continue
      }

      val parentChanPostHide = findParentNonNullChanPostHide(
        postDescriptor = replyTo,
        allPostDescriptors = allPostDescriptors,
        getPostHide = getPostHide,
        visitedPosts = visitedPosts
      )

      if (parentChanPostHide != null) {
        return parentChanPostHide
      }
    }

    return null
  }

  private fun postHideReason(
    processingCatalog: Boolean,
    postDescriptor: PostDescriptor,
    chanPostHide: ChanPostHide?
  ): String {
    if (chanPostHide == null) {
      // TODO: Filters. Remove this once filters are implemented.
      unreachable("This shouldn't be possible now but will be once filters are implemented")
    }

    if (chanPostHide.applyToReplies && postDescriptor != chanPostHide.postDescriptor) {
      return replyToHiddenPostReason(processingCatalog, postDescriptor, chanPostHide.postDescriptor)
    }

    return chanPostHide.reason
  }

  private fun canHidePost(
    processingCatalog: Boolean,
    postCellData: PostCellData,
    chanPostHide: ChanPostHide?
  ): Boolean {
    if (chanPostHide == null) {
      return false
    }

    if (chanPostHide.manuallyUnhidden) {
      return false
    }

    if (!processingCatalog && postCellData.isOP) {
      return false
    }

    return true
  }

  sealed interface PostHideResult {
    object Unchanged : PostHideResult
    data class Hide(val chanPostHide: ChanPostHide) : PostHideResult
    object Unhide : PostHideResult
  }

  companion object {
    private const val TAG = "PostFilterHelper"

    suspend fun hiddenManuallyReason(
      parsedPostDataRepository: ParsedPostDataRepository,
      chanDescriptor: ChanDescriptor,
      postDescriptor: PostDescriptor
    ): String {
      val postText = buildString {
        if (chanDescriptor is CatalogDescriptor) {
          val parsedPostSubject = parsedPostDataRepository.getParsedPostData(postDescriptor)
            ?.parsedPostSubject

          if (parsedPostSubject.isNotNullNorBlank()) {
            append(parsedPostSubject)
            return@buildString
          }
        }

        append(postDescriptor.postNo)

        if (postDescriptor.postSubNo > 0) {
          append(",")
          append(postDescriptor.postSubNo)
        }
      }

      return when (chanDescriptor) {
        is CatalogDescriptor -> "Thread \'${postText}\' hidden manually"
        is ThreadDescriptor -> "Post \'${postText}\' hidden manually"
      }
    }

    fun replyToHiddenPostReason(
      processingCatalog: Boolean,
      postDescriptor: PostDescriptor,
      replyToPostDescriptor: PostDescriptor
    ): String {
      val postType1 = if (processingCatalog) "Thread" else "Post"
      val postType2 = if (processingCatalog) "thread" else "post"

      val postDescriptorString = buildString {
        append(postDescriptor.postNo)

        if (postDescriptor.postSubNo > 0) {
          append(",")
          append(postDescriptor.postSubNo)
        }
      }

      val replyToPostDescriptorString = buildString {
        append(replyToPostDescriptor.postNo)

        if (replyToPostDescriptor.postSubNo > 0) {
          append(",")
          append(replyToPostDescriptor.postSubNo)
        }
      }

      return "${postType1} (${postDescriptorString}) hidden because it replies to another hidden ${postType2} (${replyToPostDescriptorString})"
    }
  }

}