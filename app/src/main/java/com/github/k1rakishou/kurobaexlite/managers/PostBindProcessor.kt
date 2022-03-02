package com.github.k1rakishou.kurobaexlite.managers

import com.github.k1rakishou.kurobaexlite.helpers.executors.KurobaCoroutineScope
import com.github.k1rakishou.kurobaexlite.helpers.maxOfByOrNull
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PostBindProcessor(
  private val chanThreadViewManager: ChanThreadViewManager
) {
  private val coroutineScope = KurobaCoroutineScope()
  private val activeBoundPostJobs = mutableMapOf<PostDescriptor, Job>()

  fun onPostBind(
    isCatalogMode: Boolean,
    postsParsedOnce: Boolean,
    postDescriptor: PostDescriptor
  ) {
    if (activeBoundPostJobs.containsKey(postDescriptor)) {
      return
    }

    activeBoundPostJobs[postDescriptor] = coroutineScope.launch {
      onPostBindInternal(
        isCatalogMode = isCatalogMode,
        postsParsedOnce = postsParsedOnce,
        postDescriptor = postDescriptor
      )
    }
  }

  fun onPostUnbind(isCatalogMode: Boolean, postDescriptor: PostDescriptor) {
    activeBoundPostJobs.remove(postDescriptor)?.cancel()
  }

  private suspend fun onPostBindInternal(
    isCatalogMode: Boolean,
    postsParsedOnce: Boolean,
    postDescriptor: PostDescriptor
  ) {
    if (!isCatalogMode && postsParsedOnce) {
      chanThreadViewManager.insertOrUpdate(postDescriptor.threadDescriptor) {
        val prevLastViewedPostDescriptor = lastViewedPostDescriptor

        val newLastViewedPostDescriptor: PostDescriptor? = when {
          prevLastViewedPostDescriptor == null -> postDescriptor
          prevLastViewedPostDescriptor.postNo != postDescriptor.postNo -> {
            maxOfByOrNull(prevLastViewedPostDescriptor, postDescriptor) { it.postNo }
          }
          prevLastViewedPostDescriptor.postSubNo != postDescriptor.postSubNo -> {
            maxOfByOrNull(prevLastViewedPostDescriptor, postDescriptor) { it.postSubNo }
          }
          else -> null
        }

        if (newLastViewedPostDescriptor != null) {
          lastViewedPostDescriptor = newLastViewedPostDescriptor
        }
      }
    }
  }

  companion object {
    private const val TAG = "PostBindProcessor"
  }

}