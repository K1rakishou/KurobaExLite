package com.github.k1rakishou.kurobaexlite.helpers.sort

import com.github.k1rakishou.kurobaexlite.helpers.settings.CatalogSort
import com.github.k1rakishou.kurobaexlite.helpers.settings.CatalogSortSetting
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CatalogThreadSorter : PostSorter() {

  suspend fun sortCatalogPostCellData(
    catalogThreads: Collection<PostCellData>,
    catalogSortSetting: CatalogSortSetting
  ): List<PostCellData> {
    return withContext(Dispatchers.Default) {
      return@withContext when (catalogSortSetting.sort) {
        CatalogSort.BUMP -> {
          // Reverse the "ascending" flag for BUMP sorting. We want to most recently bumped posts to go
          // first when sorting order is descending.
          sortPostCellData(
            ascending = !catalogSortSetting.ascending,
            posts = catalogThreads
          ) { postDataState -> postDataState.originalPostOrder }
        }
        CatalogSort.ACTIVITY -> {
          // Reverse the "ascending" flag for ACTIVITY sorting. The same reasoning as for BUMP.
          catalogThreads.sortedWith(ThreadPostCellDataActivityComparator(!catalogSortSetting.ascending))
        }
        CatalogSort.REPLY -> {
          sortPostCellData(
            ascending = catalogSortSetting.ascending,
            posts = catalogThreads
          ) { postDataState -> postDataState.threadRepliesTotal ?: 0 }
        }
        CatalogSort.IMAGE -> {
          sortPostCellData(
            ascending = catalogSortSetting.ascending,
            posts = catalogThreads
          ) { postDataState -> postDataState.threadImagesTotal ?: 0 }
        }
        CatalogSort.CREATION_TIME -> {
          sortPostCellData(
            ascending = catalogSortSetting.ascending,
            posts = catalogThreads
          ) { postDataState -> postDataState.timeMs ?: 0 }
        }
        CatalogSort.MODIFIED -> {
          sortPostCellData(
            ascending = catalogSortSetting.ascending,
            posts = catalogThreads
          ) { postDataState -> postDataState.lastModified ?: 0 }
        }
      }
    }
  }

  suspend fun sortCatalogPostData(
    catalogThreads: List<IPostData>,
    catalogSortSetting: CatalogSortSetting
  ): List<IPostData> {
    return withContext(Dispatchers.Default) {
      return@withContext when (catalogSortSetting.sort) {
        CatalogSort.BUMP -> {
          // Reverse the "ascending" flag for BUMP sorting. We want to most recently bumped posts to go
          // first when sorting order is descending.
          sortPostData(
            ascending = !catalogSortSetting.ascending,
            catalogThreads = catalogThreads
          ) { postDataState -> postDataState.originalPostOrder }
        }
        CatalogSort.ACTIVITY -> {
          // Reverse the "ascending" flag for ACTIVITY sorting. The same reasoning as for BUMP.
          catalogThreads.sortedWith(ThreadPostDataActivityComparator(!catalogSortSetting.ascending))
        }
        CatalogSort.REPLY -> {
          sortPostData(
            ascending = catalogSortSetting.ascending,
            catalogThreads = catalogThreads
          ) { postDataState -> postDataState.threadRepliesTotal ?: 0 }
        }
        CatalogSort.IMAGE -> {
          sortPostData(
            ascending = catalogSortSetting.ascending,
            catalogThreads = catalogThreads
          ) { postDataState -> postDataState.threadImagesTotal ?: 0 }
        }
        CatalogSort.CREATION_TIME -> {
          sortPostData(
            ascending = catalogSortSetting.ascending,
            catalogThreads = catalogThreads
          ) { postDataState -> postDataState.timeMs ?: 0 }
        }
        CatalogSort.MODIFIED -> {
          sortPostData(
            ascending = catalogSortSetting.ascending,
            catalogThreads = catalogThreads
          ) { postDataState -> postDataState.lastModified ?: 0 }
        }
      }
    }
  }

  private inline fun sortPostData(
    ascending: Boolean,
    catalogThreads: List<IPostData>,
    crossinline selector: (IPostData) -> Comparable<*>
  ): List<IPostData> {
    val comparator = if (ascending) {
      compareBy<IPostData>(selector)
    } else {
      compareByDescending<IPostData>(selector)
    }

    return catalogThreads.sortedWith(comparator)
  }

  class ThreadPostCellDataActivityComparator(
    private val ascending: Boolean
  ) : Comparator<PostCellData> {
    private val currentTimeMs: Long = System.currentTimeMillis()

    override fun compare(lhs: PostCellData, rhs: PostCellData): Int {
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

  class ThreadPostDataActivityComparator(
    private val ascending: Boolean
  ) : Comparator<IPostData> {
    private val currentTimeMs: Long = System.currentTimeMillis()

    override fun compare(lhs: IPostData, rhs: IPostData): Int {
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