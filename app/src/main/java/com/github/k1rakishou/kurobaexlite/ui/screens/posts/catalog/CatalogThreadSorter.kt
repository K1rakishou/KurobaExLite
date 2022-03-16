package com.github.k1rakishou.kurobaexlite.ui.screens.posts.catalog

import com.github.k1rakishou.kurobaexlite.helpers.settings.CatalogSort
import com.github.k1rakishou.kurobaexlite.helpers.settings.CatalogSortSetting
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData

object CatalogThreadSorter {

  fun sortCatalogThreads(catalogThreads: List<PostData>, catalogSortSetting: CatalogSortSetting): List<PostData> {
    return when (catalogSortSetting.sort) {
      CatalogSort.BUMP -> {
        // Reverse the "ascending" flag for BUMP sorting. We want to most recently bumped posts to go
        // first when sorting order is descending.
        sortPosts(
          ascending = !catalogSortSetting.ascending,
          catalogThreads = catalogThreads
        ) { postDataState -> postDataState.originalPostOrder }
      }
      CatalogSort.ACTIVITY -> {
        // Reverse the "ascending" flag for ACTIVITY sorting. The same reasoning as for BUMP.
        catalogThreads.sortedWith(ThreadActivityComparator(!catalogSortSetting.ascending))
      }
      CatalogSort.REPLY -> {
        sortPosts(
          ascending = catalogSortSetting.ascending,
          catalogThreads = catalogThreads
        ) { postDataState -> postDataState.threadRepliesTotal ?: 0 }
      }
      CatalogSort.IMAGE -> {
        sortPosts(
          ascending = catalogSortSetting.ascending,
          catalogThreads = catalogThreads
        ) { postDataState -> postDataState.threadImagesTotal ?: 0 }
      }
      CatalogSort.CREATION_TIME -> {
        sortPosts(
          ascending = catalogSortSetting.ascending,
          catalogThreads = catalogThreads
        ) { postDataState -> postDataState.timeMs ?: 0 }
      }
      CatalogSort.MODIFIED -> {
        sortPosts(
          ascending = catalogSortSetting.ascending,
          catalogThreads = catalogThreads
        ) { postDataState -> postDataState.lastModified ?: 0 }
      }

    }
  }

  private inline fun sortPosts(
    ascending: Boolean,
    catalogThreads: List<PostData>,
    crossinline selector: (PostData) -> Comparable<*>
  ): List<PostData> {
    val comparator = if (ascending) {
      compareBy<PostData>(selector)
    } else {
      compareByDescending<PostData>(selector)
    }

    return catalogThreads.sortedWith(comparator)
  }

  class ThreadActivityComparator(
    private val ascending: Boolean
  ) : Comparator<PostData> {
    private val currentTimeMs: Long = System.currentTimeMillis()

    override fun compare(lhs: PostData, rhs: PostData): Int {
      // we can't divide by zero, but we can divide by the smallest thing that's closest to 0 instead
      val eps = 0.0001f

      val lthreadRepliesTotal = lhs.threadRepliesTotal ?: 0
      val rthreadRepliesTotal = rhs.threadRepliesTotal ?: 0

      val lhsDivider = if (lthreadRepliesTotal > 0) {
        lthreadRepliesTotal.toFloat()
      } else {
        eps
      }

      val rhsDivider = if (rthreadRepliesTotal > 0) {
        rthreadRepliesTotal.toFloat()
      } else {
        eps
      }

      val score1 = ((currentTimeMs - (lhs.timeMs ?: 0)).toFloat() / lhsDivider).toLong()
      val score2 = ((currentTimeMs - (rhs.timeMs ?: 0)).toFloat() / rhsDivider).toLong()

      return if (ascending) {
        score1.compareTo(score2)
      } else {
        score2.compareTo(score1)
      }
    }

  }

}