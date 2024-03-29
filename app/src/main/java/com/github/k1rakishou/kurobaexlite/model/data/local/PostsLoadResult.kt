package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor


data class PostsLoadResult(
  val chanDescriptor: ChanDescriptor,
  val newPosts: List<IPostData>,
  val updatedPosts: List<IPostData>,
  val unchangedPosts: List<IPostData> = emptyList(),
  val deletedPosts: List<IPostData> = emptyList(),
) {
  val newPostsCount: Int
    get() = newPosts.size
  val updatedPostsCount: Int
    get() = updatedPosts.size
  val newOrUpdatedCount: Int
    get() = newPostsCount + updatedPostsCount
  val totalPostsCount: Int
    get() = newPosts.size + updatedPosts.size + unchangedPosts.size + deletedPosts.size

  // Original post is going to be updated every time a new post is added/updated in the thread
  // so we need to skip it here
  val updatePostsCountExcludingOriginalPost: Int
    get() = updatedPosts.count { postData -> !postData.isOP }

  fun firstOrNull(predicate: (IPostData) -> Boolean): IPostData? {
    iteratePostsWhile { postData ->
      if (predicate(postData)) {
        return postData
      }

      return@iteratePostsWhile true
    }

    return null
  }

  private inline fun iteratePostsWhile(iterator: (IPostData) -> Boolean) {
    for (newPost in newPosts) {
      if (!iterator(newPost)) {
        return
      }
    }

    for (newPost in updatedPosts) {
      if (!iterator(newPost)) {
        return
      }
    }

    for (newPost in unchangedPosts) {
      if (!iterator(newPost)) {
        return
      }
    }

    for (newPost in deletedPosts) {
      if (!iterator(newPost)) {
        return
      }
    }
  }

  fun newAndUpdatedCombined(): List<IPostData> {
    val resultList = mutableListWithCap<IPostData>(newPosts.size + updatedPosts.size)

    resultList.addAll(newPosts)
    resultList.addAll(updatedPosts)

    return resultList
  }

  fun allCombinedForCatalog(): List<IPostData> {
    val resultList = mutableListWithCap<IPostData>(newPosts.size + updatedPosts.size + unchangedPosts.size)

    resultList.addAll(newPosts)
    resultList.addAll(updatedPosts)
    resultList.addAll(unchangedPosts)

    return resultList
  }

  fun allCombinedForThread(): List<IPostData> {
    val resultList = mutableListWithCap<IPostData>(deletedPosts.size + newPosts.size + updatedPosts.size + unchangedPosts.size)

    resultList.addAll(deletedPosts)
    resultList.addAll(unchangedPosts)
    resultList.addAll(updatedPosts)
    resultList.addAll(newPosts)
    resultList.sortBy { postData -> postData.postDescriptor }

    return resultList
  }

  fun newPostsCountSinceLastLoaded(lastLoadedPostDescriptor: PostDescriptor?): Int {
    if (lastLoadedPostDescriptor == null) {
      return newPosts.size
    }

    return newPosts.count { postData -> postData.postDescriptor > lastLoadedPostDescriptor }
  }

  fun deletedPostsCountSinceLastLoaded(lastLoadedPostDescriptor: PostDescriptor?): Int {
    return deletedPosts.count { postData ->
      if (lastLoadedPostDescriptor == null) {
        return@count true
      }

      return@count postData.postDescriptor > lastLoadedPostDescriptor
    }
  }

  fun isEmpty(): Boolean {
    return deletedPosts.isEmpty() && newPosts.isEmpty() && updatedPosts.isEmpty() && unchangedPosts.isEmpty()
  }

  fun isNotEmpty(): Boolean {
    return !isEmpty()
  }

  fun info(): String {
    return "newPosts=${newPosts.size}, updatedPosts=${updatedPosts.size}, " +
      "unchangedPosts=${unchangedPosts.size}, deletedPosts=${deletedPosts.size}"
  }

}