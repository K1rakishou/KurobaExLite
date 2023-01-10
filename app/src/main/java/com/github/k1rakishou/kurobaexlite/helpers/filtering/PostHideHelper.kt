package com.github.k1rakishou.kurobaexlite.helpers.filtering

import androidx.annotation.VisibleForTesting
import com.github.k1rakishou.kurobaexlite.helpers.util.linkedMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.IPostHideRepository
import com.github.k1rakishou.kurobaexlite.model.repository.IPostReplyChainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
          postHideRepository.update(postProcessResult.toUnhide) { chanPostHide -> chanPostHide.unhidePost() }
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

    val postHidesAsMapBeforeFiltering = postHideRepository.postHidesForChanDescriptor(chanDescriptor)
    if (postHidesAsMapBeforeFiltering.isEmpty()) {
      logcat(TAG) { "filterPosts() postHidesAsMapBeforeFiltering is empty" }
      return PostProcessResult(changedPosts)
    }

    // TODO: Filters. Also checks filters here.

    val changedPostsAsLinkedMap = linkedMapWithCap<PostDescriptor, PostCellData>(changedPosts.size)
    changedPosts.forEach { postCellData -> changedPostsAsLinkedMap[postCellData.postDescriptor] = postCellData }

    val postHidesAsMap = postHidesAsMapBeforeFiltering
      .filter { (_, chanPostHide) -> changedPostsAsLinkedMap.containsKey(chanPostHide.postDescriptor) }

    if (postHidesAsMap.isEmpty()) {
      logcat(TAG) { "filterPosts() postHidesAsMap is empty" }
      return PostProcessResult(changedPosts)
    }

    val mutex = Mutex()
    val processingCatalog = chanDescriptor is CatalogDescriptor

    val toHide = mutableMapOf<PostDescriptor, ChanPostHide>()
    val toUnhide = mutableSetOf<PostDescriptor>()
    val postHides = postHidesAsMap.values.toList()

    for (chanPostHide in postHides) {
      chanPostHide.removeRepliesMatching(toUnhide)

      processSinglePost(
        processingCatalog = processingCatalog,
        chanPostHide = chanPostHide,
        getPostCellData = { postDescriptor ->
          mutex.withLock { changedPostsAsLinkedMap[postDescriptor] ?: postCellDataByPostDescriptor?.invoke(postDescriptor) }
        },
        setPostCellData = { postCellData ->
          mutex.withLock { changedPostsAsLinkedMap[postCellData.postDescriptor] = postCellData }
        },
        addPostHide = { newChanPostHide ->
          mutex.withLock { toHide[newChanPostHide.postDescriptor] = newChanPostHide }
        },
        addPostUnhide = { newPostDescriptor ->
          mutex.withLock { toUnhide += newPostDescriptor }
        },
        getChanPostHide = { postDescriptor ->
          mutex.withLock { postHidesAsMap[postDescriptor] ?: toHide[postDescriptor] }
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
    getPostCellData: suspend (PostDescriptor) -> PostCellData?,
    setPostCellData: suspend (PostCellData) -> Unit,
    addPostHide: suspend (ChanPostHide) -> Unit,
    addPostUnhide: suspend (PostDescriptor) -> Unit,
    getChanPostHide: suspend (PostDescriptor) -> ChanPostHide?
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

    val repliesMap = postReplyChainRepository.getAllRepliesFromRecursively(thisPostDescriptor)
    if (repliesMap.isEmpty()) {
      // Post has no replies to it, do nothing
      return
    }

    for ((parentPostDescriptor, repliesFrom) in repliesMap.entries) {
      for (replyPostDescriptor in repliesFrom) {
        if (replyPostDescriptor.isOP) {
          // Do not allow hiding OP in threads. Technically it's impossible to have an OP replying to OP but just in case
          // let's handle this theoretical situation.
          return
        }

        processReply(
          parentPostDescriptor = parentPostDescriptor,
          childPostDescriptor = replyPostDescriptor,
          parentChanPostHide = chanPostHide,
          getPostCellData = getPostCellData,
          setPostCellData = setPostCellData,
          addPostHide = addPostHide,
          addPostUnhide = addPostUnhide,
          getChanPostHide = getChanPostHide
        )
      }
    }
  }

  private suspend fun processReply(
    parentPostDescriptor: PostDescriptor,
    childPostDescriptor: PostDescriptor,
    parentChanPostHide: ChanPostHide,
    getPostCellData: suspend (PostDescriptor) -> PostCellData?,
    setPostCellData: suspend (PostCellData) -> Unit,
    addPostHide: suspend (ChanPostHide) -> Unit,
    addPostUnhide: suspend (PostDescriptor) -> Unit,
    getChanPostHide: suspend (PostDescriptor) -> ChanPostHide?
  ) {
    val childPostCellData = getPostCellData(childPostDescriptor)
      ?: return

    val childChanPostHide = getChanPostHide(childPostDescriptor)
    val repliesToHiddenPostsContain = childChanPostHide?.repliesToHiddenPostsContain(parentPostDescriptor) ?: false

    val parentIsHidden = parentChanPostHide.isHidden()
    val childIsHidden = childChanPostHide != null && childChanPostHide.isHidden()

    if (!parentIsHidden && (!childIsHidden && !repliesToHiddenPostsContain)) {
      // Parent and child are not hidden and child's ChanPostHide does not contain parent's post descriptor
      return
    }

    if (parentIsHidden && (childIsHidden && repliesToHiddenPostsContain)) {
      // Parent and child are hidden child's ChanPostHide contains parent's post descriptor
      return
    }

    if (parentIsHidden) {
      // Parent is hidden and child is not, we need to hide the child
      var newOrUpdateChanPostHide = childChanPostHide
      if (newOrUpdateChanPostHide == null) {
        newOrUpdateChanPostHide = ChanPostHide(
          postDescriptor = childPostDescriptor,
          applyToReplies = parentChanPostHide.applyToReplies,
          state = ChanPostHide.State.Unspecified
        )
      }

      newOrUpdateChanPostHide.addReplies(listOf(parentPostDescriptor))
      addPostHide(newOrUpdateChanPostHide)
      setPostCellData(childPostCellData.copy(postHideUi = newOrUpdateChanPostHide.toPostHideUi()))
    } else {
      checkNotNull(childChanPostHide) { "childChanPostHide is null" }

      // Parent is not hidden and child is, we need to check whether the child can be unhidden. For that we need:
      // 1. Check whether the child is hidden manually.
      // 2. Try to find any ChanPostHide that is currently hidden and has applyToReplies set to true for each post this
      //    post replies to (traverse upwards). If there are none then this post is not hidden anymore.
      //    If there is at least one then it's hidden.
      // If any of the above are true then the child must stay hidden. Otherwise unhide the child post.

      if (childChanPostHide.state == ChanPostHide.State.HiddenManually) {
        // Post is hidden manually.

        childChanPostHide.addReplies(listOf(parentPostDescriptor))
        setPostCellData(childPostCellData.copy(postHideUi = childChanPostHide.toPostHideUi()))
        return
      }

      if (childChanPostHide.state == ChanPostHide.State.UnhiddenManually) {
        childChanPostHide.clearPostHides()
        addPostUnhide(childPostDescriptor)
        setPostCellData(childPostCellData.copy(postHideUi = childChanPostHide.toPostHideUi()))
        return
      }

      val repliesTo = postReplyChainRepository.getRepliesTo(childChanPostHide.postDescriptor)

      val firstNonNullParentChanPostHide = findFirstNonNullHiddenParentChanPostHide(
        alreadyVisited = mutableSetWithCap<PostDescriptor>(repliesTo.size * 2),
        repliesTo = repliesTo,
        getChanPostHide = getChanPostHide
      )

      if (firstNonNullParentChanPostHide != null) {
        // Post does not reply to any hidden posts.

        childChanPostHide.addReplies(listOf(parentPostDescriptor))
        setPostCellData(childPostCellData.copy(postHideUi = childChanPostHide.toPostHideUi()))
        return
      }

      childChanPostHide.removeReplies(listOf(parentPostDescriptor))
      addPostUnhide(childPostDescriptor)
      setPostCellData(childPostCellData.copy(postHideUi = childChanPostHide.toPostHideUi()))
    }
  }

  private suspend fun findFirstNonNullHiddenParentChanPostHide(
    alreadyVisited: MutableSet<PostDescriptor>,
    repliesTo: Set<PostDescriptor>,
    getChanPostHide: suspend (PostDescriptor) -> ChanPostHide?
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
        alreadyVisited = alreadyVisited,
        repliesTo = postReplyChainRepository.getRepliesTo(replyTo),
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