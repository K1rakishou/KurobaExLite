package com.github.k1rakishou.kurobaexlite.helpers.filtering

import androidx.annotation.VisibleForTesting
import com.github.k1rakishou.kurobaexlite.helpers.util.linkedMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.IPostHideRepository
import com.github.k1rakishou.kurobaexlite.model.repository.IPostReplyChainRepository
import com.github.k1rakishou.kurobaexlite.model.repository.ThreadReplyChainCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.logcat
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class PostHideHelper(
  private val postHideRepository: IPostHideRepository,
  private val postReplyChainRepository: IPostReplyChainRepository
) {

  @OptIn(ExperimentalTime::class)
  suspend fun filterPosts(
    chanDescriptor: ChanDescriptor,
    changedPosts: List<PostCellData>,
    postCellDataByPostDescriptor: ((PostDescriptor) -> PostCellData?)?
  ): List<PostCellData> {
    logcat(TAG) { "filterPosts() processing..." }

    val (resultPosts, duration) = measureTimedValue {
      withContext(Dispatchers.Default) {
        val postProcessResult = processPosts(
          chanDescriptor = chanDescriptor,
          changedPosts = changedPosts,
          postCellDataByPostDescriptor = postCellDataByPostDescriptor
        )

        if (postProcessResult.toHide.isNotEmpty()) {
          postHideRepository.createOrUpdate(chanDescriptor, postProcessResult.toHide)
        }

        if (postProcessResult.toUnhide.isNotEmpty()) {
          val toDelete = mutableSetOf<PostDescriptor>()

          postHideRepository.update(postProcessResult.toUnhide) { chanPostHide ->
            val updatedChanPostHide = chanPostHide.unhidePost()
            if (!updatedChanPostHide.isHidden()) {
              toDelete += chanPostHide.postDescriptor
            }

            return@update updatedChanPostHide
          }

          if (toDelete.isNotEmpty()) {
            postHideRepository.delete(toDelete)
          }
        }

        return@withContext postProcessResult.posts
      }
    }

    logcat(TAG) { "filterPosts() processing... done, took ${duration}" }
    return resultPosts
  }

  @VisibleForTesting
  suspend fun processPosts(
    chanDescriptor: ChanDescriptor,
    changedPosts: List<PostCellData>,
    postCellDataByPostDescriptor: ((PostDescriptor) -> PostCellData?)? = null
  ): PostProcessResult {
    if (changedPosts.isEmpty()) {
      logcat(TAG) { "filterPosts() posts is empty" }
      return PostProcessResult(changedPosts)
    }

    val postHidesAsMap = postHideRepository.postHidesForChanDescriptor(chanDescriptor)
    if (postHidesAsMap.isEmpty()) {
      logcat(TAG) { "filterPosts() postHidesAsMap is empty" }
      return PostProcessResult(changedPosts)
    }

    // TODO: Filters. Also checks filters here.

    val changedPostsAsLinkedMap = linkedMapWithCap<PostDescriptor, PostCellData>(changedPosts.size)
    changedPosts.forEach { postCellData -> changedPostsAsLinkedMap[postCellData.postDescriptor] = postCellData }

    val processingCatalog = chanDescriptor is CatalogDescriptor

    val toHide = mutableMapOf<PostDescriptor, ChanPostHide>()
    val toUnhide = mutableSetOf<PostDescriptor>()
    val postHides = postHidesAsMap.values.toList()

    val threadReplyChainCopy = if (chanDescriptor is ThreadDescriptor) {
      postReplyChainRepository.copyThreadReplyChain(chanDescriptor)
    } else {
      null
    }

    for (chanPostHide in postHides) {
      chanPostHide.removeRepliesMatching(toUnhide)

      processSinglePost(
        processingCatalog = processingCatalog,
        chanPostHide = chanPostHide,
        threadReplyChainCopy = threadReplyChainCopy,
        getPostCellData = { postDescriptor -> changedPostsAsLinkedMap[postDescriptor] ?: postCellDataByPostDescriptor?.invoke(postDescriptor) },
        setPostCellData = { postCellData -> changedPostsAsLinkedMap[postCellData.postDescriptor] = postCellData },
        addPostHide = { newChanPostHide -> toHide[newChanPostHide.postDescriptor] = newChanPostHide },
        addPostUnhide = { newPostDescriptor -> toUnhide += newPostDescriptor },
        getChanPostHide = { postDescriptor -> postHidesAsMap[postDescriptor] ?: toHide[postDescriptor] },
        retainRepliesNotHavingChanPostHide = { repliesFrom ->
          if (repliesFrom.isEmpty()) {
            return@processSinglePost emptyList()
          }

          return@processSinglePost repliesFrom.filter { postDescriptor -> !postHidesAsMap.containsKey(postDescriptor) }
        }
      )
    }

    logcat(TAG) { "filterPosts() postHides: ${postHides.size}, toHide: ${toHide.size}, toUnhide: ${toUnhide.size}" }

    return PostProcessResult(
      posts = changedPostsAsLinkedMap.values.toList(),
      toHide = toHide.values.toList(),
      toUnhide = toUnhide
    )
  }

  private suspend fun processSinglePost(
    processingCatalog: Boolean,
    chanPostHide: ChanPostHide,
    threadReplyChainCopy: ThreadReplyChainCopy?,
    getPostCellData: (PostDescriptor) -> PostCellData?,
    setPostCellData: (PostCellData) -> Unit,
    addPostHide: (ChanPostHide) -> Unit,
    addPostUnhide: (PostDescriptor) -> Unit,
    getChanPostHide: (PostDescriptor) -> ChanPostHide?,
    retainRepliesNotHavingChanPostHide: (Set<PostDescriptor>) -> List<PostDescriptor>
  ) {
    val thisPostDescriptor = chanPostHide.postDescriptor

    val postCellData = getPostCellData(thisPostDescriptor)
      ?: return

    if (!processingCatalog && thisPostDescriptor.isOP) {
      // Do not allow hiding OP in threads
      return
    }

    val wasHidden = postCellData.postHideUi != null
    val postHideUi = chanPostHide.takeIf { chanPostHide -> chanPostHide.isHidden() }?.toPostHideUi()
    val nowHidden = postHideUi != null

    if (wasHidden != nowHidden || postCellData.postHideUi != postHideUi) {
      if (nowHidden) {
        addPostHide(chanPostHide)
      } else {
        addPostUnhide(thisPostDescriptor)
      }

      setPostCellData(postCellData.copy(postHideUi = postHideUi))
    }

    if (processingCatalog) {
      // Do not process replies when filtering catalog threads (makes no sense)
      return
    }

    if (!chanPostHide.applyToReplies) {
      // Do not process replies if chanPostHide explicitly states to not process replies
      return
    }

    val allRepliesFrom = threadReplyChainCopy?.getAllRepliesFromRecursively(thisPostDescriptor)
    if (allRepliesFrom.isNullOrEmpty()) {
      // Post has no replies to it from other posts, do nothing
      return
    }

    val repliesFrom = retainRepliesNotHavingChanPostHide(allRepliesFrom).sorted()
    if (repliesFrom.isEmpty()) {
      return
    }

    for (replyFrom in repliesFrom) {
      if (thisPostDescriptor == replyFrom) {
        continue
      }

      val childPostCellData = getPostCellData(replyFrom)
        ?: continue

      val childChanPostHide = getChanPostHide(replyFrom)

      val repliesTo = threadReplyChainCopy.getRepliesTo(replyFrom)
      val firstNonNullParentChanPostHide = findFirstNonNullHiddenParentChanPostHide(
        threadReplyChainCopy = threadReplyChainCopy,
        alreadyVisited = mutableSetWithCap<PostDescriptor>(repliesTo.size * 2),
        repliesTo = repliesTo,
        getChanPostHide = getChanPostHide
      )

      if (firstNonNullParentChanPostHide != null && firstNonNullParentChanPostHide.isHidden()) {
        var newOrUpdateChanPostHide = childChanPostHide
        if (newOrUpdateChanPostHide == null) {
          newOrUpdateChanPostHide = ChanPostHide(
            postDescriptor = replyFrom,
            applyToReplies = firstNonNullParentChanPostHide.applyToReplies,
            state = ChanPostHide.State.Unspecified
          )
        }

        val hiddenReplies = filterHiddenReplies(repliesTo, getChanPostHide)
        newOrUpdateChanPostHide.addReplies(hiddenReplies)
        addPostHide(newOrUpdateChanPostHide)
        setPostCellData(childPostCellData.copy(postHideUi = newOrUpdateChanPostHide.toPostHideUi()))
      } else {
        childChanPostHide?.clearPostHides()
        addPostUnhide(replyFrom)
        setPostCellData(childPostCellData.copy(postHideUi = childChanPostHide?.toPostHideUi()))
      }
    }
  }

  private fun filterHiddenReplies(
    repliesTo: Set<PostDescriptor>,
    getChanPostHide: (PostDescriptor) -> ChanPostHide?
  ): List<PostDescriptor> {
    val filteredPostDescriptors = mutableListWithCap<PostDescriptor>(repliesTo.size)

    repliesTo.forEach { reply ->
      val chanPostHide = getChanPostHide(reply)
      if (chanPostHide != null && chanPostHide.isHidden()) {
        filteredPostDescriptors += reply
      }
    }

    return filteredPostDescriptors
  }

  private suspend fun findFirstNonNullHiddenParentChanPostHide(
    threadReplyChainCopy: ThreadReplyChainCopy,
    alreadyVisited: MutableSet<PostDescriptor>,
    repliesTo: Set<PostDescriptor>,
    getChanPostHide: (PostDescriptor) -> ChanPostHide?
  ): ChanPostHide? {
    for (replyTo in repliesTo) {
      if (!alreadyVisited.add(replyTo)) {
        continue
      }

      var chanPostHide = getChanPostHide(replyTo)
      if (chanPostHide != null && chanPostHide.applyToReplies && chanPostHide.isHidden()) {
        return chanPostHide
      }

      chanPostHide = findFirstNonNullHiddenParentChanPostHide(
        threadReplyChainCopy = threadReplyChainCopy,
        alreadyVisited = alreadyVisited,
        repliesTo = threadReplyChainCopy.getRepliesTo(replyTo),
        getChanPostHide = getChanPostHide
      )

      if (chanPostHide != null) {
        return chanPostHide
      }
    }

    return null
  }

  data class PostProcessResult(
    val posts: List<PostCellData>,
    val toHide: List<ChanPostHide> = emptyList(),
    val toUnhide: Set<PostDescriptor> = emptySet()
  )

  companion object {
    private const val TAG = "PostHideHelper"
  }

}