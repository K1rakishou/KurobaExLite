package com.github.k1rakishou.kurobaexlite.managers

import androidx.annotation.GuardedBy
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.github.k1rakishou.kurobaexlite.helpers.buffer
import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.sort.CatalogThreadSorter
import com.github.k1rakishou.kurobaexlite.helpers.sort.ThreadPostSorter
import com.github.k1rakishou.kurobaexlite.model.cache.ChanCache
import com.github.k1rakishou.kurobaexlite.model.cache.ParsedPostDataCache
import com.github.k1rakishou.kurobaexlite.model.data.IPostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat

class FastScrollerMarksManager(
  private val appScope: CoroutineScope,
  private val appSettings: AppSettings,
  private val parsedPostDataCache: ParsedPostDataCache,
  private val chanCache: ChanCache
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val fastScrollerMarkMap = linkedMapOf<ChanDescriptor, FastScrollerMarksMut>()

  private val _marksUpdatedEventFlow = MutableSharedFlow<ChanDescriptor>()
  val marksUpdatedEventFlow: SharedFlow<ChanDescriptor>
    get() = _marksUpdatedEventFlow.asSharedFlow()

  init {
    appScope.launch {
      parsedPostDataCache.chanDescriptorPostsUpdatedFlow
        .buffer(1.seconds, emitIfEmpty = false)
        .collect { chanDescriptors ->
          chanDescriptors.toSet().forEach { chanDescriptor ->
            processSingleChanDescriptor(chanDescriptor)
          }
        }
    }
  }

  suspend fun getFastScrollerMarks(chanDescriptor: ChanDescriptor): FastScrollerMarks? {
    val marksList = mutex.withLock { fastScrollerMarkMap[chanDescriptor]?.marks?.toList() }
    if (marksList.isNullOrEmpty()) {
      return null
    }

    return FastScrollerMarks(marksList)
  }

  private suspend fun processSingleChanDescriptor(
    chanDescriptor: ChanDescriptor
  ) {
    val postDescriptors = when (chanDescriptor) {
      is CatalogDescriptor -> chanCache.getCatalogThreads(chanDescriptor)
        .map { it.postDescriptor }
      is ThreadDescriptor -> chanCache.getThreadPosts(chanDescriptor)
        .map { it.postDescriptor }
    }

    logcat(TAG) {
      "postDataUpdatesFlow new event " +
        "chanDescriptor: ${chanDescriptor}, " +
        "postDescriptors: ${postDescriptors.size}"
    }

    mutex.withLock {
      removeOld()
      removeCurrent(chanDescriptor)
    }
    withContext(Dispatchers.Default) { processPosts(chanDescriptor, postDescriptors) }
    _marksUpdatedEventFlow.emit(chanDescriptor)

    mutex.withLock {
      val marksList = fastScrollerMarkMap[chanDescriptor]?.marks
        ?: emptyList()

      logcat(TAG) {
        "postDataUpdatesFlow new event " +
          "chanDescriptor: ${chanDescriptor}, " +
          "postDescriptors: ${postDescriptors.size}, " +
          "marksList: ${marksList.size}"
      }
    }
  }

  private fun removeCurrent(chanDescriptor: ChanDescriptor) {
    require(mutex.isLocked) { "Mutex must be locked!" }

    fastScrollerMarkMap.remove(chanDescriptor)
  }

  private fun removeOld() {
    require(mutex.isLocked) { "Mutex must be locked!" }

    if (fastScrollerMarkMap.size <= 10) {
      return
    }

    var toSkip = (fastScrollerMarkMap.size - 5).coerceAtLeast(5)
    val toDelete = mutableListOf<ChanDescriptor>()

    fastScrollerMarkMap.forEach { (descriptor, _)  ->
      if (toSkip > 0) {
        --toSkip
        return@forEach
      }

      toDelete += descriptor
    }

    toDelete.forEach { chanDescriptor -> fastScrollerMarkMap.remove(chanDescriptor) }
  }

  private suspend fun processPosts(
    chanDescriptor: ChanDescriptor,
    postDescriptors: Collection<PostDescriptor>
  ) {
    var fastScrollerMark: FastScrollerMarkMut? = null

    val posts = when (chanDescriptor) {
      is CatalogDescriptor -> {
        val catalogSortSetting = appSettings.catalogSort.read()

        CatalogThreadSorter.sortCatalogPostData(
          catalogThreads = chanCache.getCatalogThreads(chanDescriptor),
          catalogSortSetting = catalogSortSetting
        )
      }
      is ThreadDescriptor -> {
        ThreadPostSorter.sortThreadPostData(
          threadPosts = chanCache.getThreadPosts(chanDescriptor)
        )
      }
    }

    for (postDescriptor in postDescriptors) {
      val prevFastScrollerMark = fastScrollerMark

      val parsedPostData = parsedPostDataCache.getParsedPostData(postDescriptor)
      if (parsedPostData == null) {
        if (prevFastScrollerMark != null) {
          val index = getPostIndex(posts, postDescriptor)
          addOrUpdateMark(chanDescriptor, index, prevFastScrollerMark)
          fastScrollerMark = null
        }

        continue
      }

      val newType = when {
        parsedPostData.isPostMarkedAsMine -> FastScrollerMarkType.MyPost
        parsedPostData.isReplyToPostMarkedAsMine -> FastScrollerMarkType.ReplyToMyPost
        else -> null
      }

      if (prevFastScrollerMark == null) {
        if (newType == null) {
          continue
        }

        val index = getPostIndex(posts, postDescriptor)
          ?: continue

        fastScrollerMark = FastScrollerMarkMut(
          startPosition = index,
          endPosition = index,
          type = newType
        )

        continue
      }

      if (newType == prevFastScrollerMark.type) {
        val index = getPostIndex(posts, postDescriptor)
          ?: continue

        prevFastScrollerMark.endPosition = index
        continue
      }

      val index = getPostIndex(posts, postDescriptor)
        ?: continue

      addOrUpdateMark(chanDescriptor, index, prevFastScrollerMark)

      if (newType != null) {
        fastScrollerMark = FastScrollerMarkMut(
          startPosition = index,
          endPosition = index,
          type = newType
        )

        continue
      }

      fastScrollerMark = null
    }

    fastScrollerMark?.let { mark -> addOrUpdateMark(chanDescriptor, null, mark) }
    fastScrollerMark = null
  }

  private fun getPostIndex(posts: List<IPostData>, postDescriptor: PostDescriptor): Int? {
    return posts
      .indexOfFirst { postData -> postData.postDescriptor == postDescriptor }
      .takeIf { index -> index >= 0 }
  }

  private suspend fun addOrUpdateMark(
    chanDescriptor: ChanDescriptor,
    index: Int?,
    prevFastScrollerMark: FastScrollerMarkMut
  ) {
    mutex.withLock {
      val markList = fastScrollerMarkMap.getOrPut(
        key = chanDescriptor,
        defaultValue = { FastScrollerMarksMut(mutableListWithCap(128)) }
      ).marks

      val prevMarkIndex = if (index != null) {
        markList.indexOfFirst { fastScrollerMark -> fastScrollerMark.startPosition == index }
      } else {
        -1
      }

      if (prevMarkIndex >= 0) {
        markList[prevMarkIndex] = FastScrollerMark(
          startPosition = prevFastScrollerMark.startPosition,
          endPosition = prevFastScrollerMark.endPosition,
          type = prevFastScrollerMark.type
        )
      } else {
        markList += FastScrollerMark(
          startPosition = prevFastScrollerMark.startPosition,
          endPosition = prevFastScrollerMark.endPosition,
          type = prevFastScrollerMark.type
        )
      }
    }
  }

  class FastScrollerMarks(
    val marks: List<FastScrollerMark>
  )

  private class FastScrollerMarksMut(
    val marks: MutableList<FastScrollerMark>
  )

  @Immutable
  data class FastScrollerMark(
    val startPosition: Int = 0,
    val endPosition: Int = 0,
    val type: FastScrollerMarkType
  )

  data class FastScrollerMarkMut(
    var startPosition: Int = 0,
    var endPosition: Int = 0,
    val type: FastScrollerMarkType
  ) {

    fun toFastScrollerMark(): FastScrollerMark {
      return FastScrollerMark(
        startPosition = startPosition,
        endPosition = endPosition,
        type = type
      )
    }

  }

  enum class FastScrollerMarkType(val color: Color) {
    MyPost(Color(0xFF9BF853L)),
    ReplyToMyPost(Color(0xFFF85E53))
  }

  companion object {
    private const val TAG = "FastScrollerMarksManager"
  }
}