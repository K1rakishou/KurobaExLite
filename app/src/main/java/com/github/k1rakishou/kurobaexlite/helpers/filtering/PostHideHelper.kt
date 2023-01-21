package com.github.k1rakishou.kurobaexlite.helpers.filtering

import androidx.annotation.VisibleForTesting
import com.github.k1rakishou.kurobaexlite.helpers.util.linkedMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableIteration
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.IPostHideRepository
import com.github.k1rakishou.kurobaexlite.model.repository.IPostReplyChainRepository
import com.github.k1rakishou.kurobaexlite.model.repository.ThreadReplyChainCopy
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.concurrent.Executors
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
      withContext(filterDispatcher) {
        val postProcessResult = processPosts(
          chanDescriptor = chanDescriptor,
          changedPosts = changedPosts,
          postCellDataByPostDescriptor = postCellDataByPostDescriptor
        )

        if (postProcessResult.toHide.isNotEmpty()) {
          postHideRepository.createOrUpdate(chanDescriptor, postProcessResult.toHide).unwrap()
        }

        if (postProcessResult.toDelete.isNotEmpty()) {
          postHideRepository.delete(postProcessResult.toDelete).unwrap()
        }

        if (postProcessResult.toUnhide.isNotEmpty()) {
          postHideRepository
            .update(postProcessResult.toUnhide) { chanPostHide -> chanPostHide.unhidePost() }
            .unwrap()
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

    val postHidesAsMap = when (chanDescriptor) {
      is CatalogDescriptor -> postHideRepository.postHidesForCatalog(chanDescriptor, changedPosts.map { it.postDescriptor })
      is ThreadDescriptor -> postHideRepository.postHidesForThread(chanDescriptor)
    }

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
        getPostCellData = { postDescriptor ->
          return@processSinglePost changedPostsAsLinkedMap[postDescriptor]
            ?: postCellDataByPostDescriptor?.invoke(postDescriptor)
        },
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

    val toDelete = mutableSetOf<PostDescriptor>()
    processPostHidesToDelete(postHidesAsMap, toDelete)

    // If a PostDescriptor exists in both toUnhide and toDelete then remove it from toUnhide since we are going to delete
    // it anyway to do less work
    toUnhide.mutableIteration { mutableIterator, postDescriptor ->
      if (postDescriptor in toDelete) {
        mutableIterator.remove()
      }

      return@mutableIteration true
    }

    logcat(TAG) {
      "filterPosts() postHides: ${postHides.size}, " +
        "toHide: ${toHide.size}, " +
        "toUnhide: ${toUnhide.size}, " +
        "toDelete: ${toDelete.size}"
    }

    return PostProcessResult(
      posts = changedPostsAsLinkedMap.values.toList(),
      toHide = toHide.values.toList(),
      toUnhide = toUnhide,
      toDelete = toDelete
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

        if (newOrUpdateChanPostHide.isHidden()) {
          addPostHide(newOrUpdateChanPostHide)
          setPostCellData(childPostCellData.copy(postHideUi = newOrUpdateChanPostHide.toPostHideUi()))
        }
      } else {
        childChanPostHide?.clearPostHides()

        if (childChanPostHide == null || !childChanPostHide.isHidden()) {
          addPostUnhide(replyFrom)
          setPostCellData(childPostCellData.copy(postHideUi = childChanPostHide?.toPostHideUi()))
        }
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

  private fun processPostHidesToDelete(
    postHidesAsMap: Map<PostDescriptor, ChanPostHide>,
    toDelete: MutableSet<PostDescriptor>
  ) {
    val alreadyVisited = mutableSetWithCap<PostDescriptor>(128)

    for ((postDescriptor, _) in postHidesAsMap.entries) {
      alreadyVisited.clear()

      val rootUnhiddenPostHide = findRootUnhiddenPostHide(postDescriptor, alreadyVisited, postHidesAsMap)
      if (rootUnhiddenPostHide != null) {
        toDelete += postDescriptor
      }
    }
  }

  private fun findRootUnhiddenPostHide(
    postDescriptor: PostDescriptor,
    alreadyVisited: MutableSet<PostDescriptor>,
    postHidesAsMap: Map<PostDescriptor, ChanPostHide>,
  ): ChanPostHide? {
    if (!alreadyVisited.add(postDescriptor)) {
      return null
    }

    val chanPostHide = postHidesAsMap[postDescriptor]
      ?: return null

    if (chanPostHide.isRoot() && !chanPostHide.isHidden()) {
      return chanPostHide
    }

    for (replyToHiddenPost in chanPostHide.repliesToHiddenPostsUnsafe) {
      val innerChanPostHide = findRootUnhiddenPostHide(replyToHiddenPost, alreadyVisited, postHidesAsMap)
      if (innerChanPostHide != null) {
        return innerChanPostHide
      }
    }

    return null
  }

  data class PostProcessResult(
    val posts: List<PostCellData>,
    val toHide: List<ChanPostHide> = emptyList(),
    val toUnhide: Set<PostDescriptor> = emptySet(),
    val toDelete: Set<PostDescriptor> = emptySet(),
  )

  companion object {
    private const val TAG = "PostHideHelper"

    private val filterDispatcher = Executors
      .newFixedThreadPool(1, { runnable -> Thread(runnable, "FilterThread") })
      .asCoroutineDispatcher()
  }

}