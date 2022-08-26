package com.github.k1rakishou.kurobaexlite.interactors.bookmark

import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentParser
import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPartSpan
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableMapWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableSetWithCap
import com.github.k1rakishou.kurobaexlite.managers.SiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPost
import com.github.k1rakishou.kurobaexlite.model.data.local.MarkedPostType
import com.github.k1rakishou.kurobaexlite.model.database.KurobaExLiteDatabase
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class YousPerThreadMap(
  val map: Map<ThreadDescriptor, Map<PostDescriptor, List<ExtractRepliesToMyPosts.ReplyToMyPost>>>
)

class ExtractRepliesToMyPosts(
  private val appScope: CoroutineScope,
  private val postCommentParser: PostCommentParser,
  private val siteManager: SiteManager,
  private val kurobaExLiteDatabase: KurobaExLiteDatabase
) {

  suspend fun await(
    successFetches: List<FetchThreadBookmarkInfo.ThreadBookmarkFetchResult.Success>
  ): Result<YousPerThreadMap> {
    return Result.Try {
      val cap = successFetches.size
      val quotesToMePerThreadMap =
        mutableMapWithCap<ThreadDescriptor, Map<PostDescriptor, List<ReplyToMyPost>>>(cap)
      val mutex = Mutex()

      successFetches
        .chunked(BATCH_SIZE)
        .forEach { chunk ->
          chunk.map { successFetchResult ->
            appScope.async(Dispatchers.IO) {
              Result.Try {
                val threadDescriptor = successFetchResult.threadDescriptor
                val quotesToMeInThreadMap = parsePostRepliesWorker(successFetchResult)

                mutex.withLock { quotesToMePerThreadMap[threadDescriptor] = quotesToMeInThreadMap }
              }.onFailure { error ->
                logcatError(TAG) {
                  "Error parsing post replies, error: ${error.asLogIfImportantOrErrorMessage()}"
                }
              }
            }
          }.awaitAll()
        }

      return@Try YousPerThreadMap(quotesToMePerThreadMap)
    }
  }

  private suspend fun parsePostRepliesWorker(
    successFetchResult: FetchThreadBookmarkInfo.ThreadBookmarkFetchResult.Success
  ): Map<PostDescriptor, List<ReplyToMyPost>> {
    val threadDescriptor = successFetchResult.threadDescriptor

    if (siteManager.bySiteKey(threadDescriptor.siteKey) == null) {
      return emptyMap()
    }

    // Key - postNo of a post that quotes other posts.
    // Value - set of postNo that the "Key" quotes.
    val quoteOwnerPostsMap = mutableMapWithCap<PostDescriptor, MutableSet<ReplyToMyPost>>(32)

    successFetchResult.threadBookmarkData.postObjects.forEach { simplePostObject ->
      val extractedQuotes = postCommentParser.parsePostComment(
        simplePostObject.comment(),
        simplePostObject.postDescriptor()
      ).flatMap { textPart -> textPart.spans.filterIsInstance<TextPartSpan.Linkable.Quote>() }

      extractedQuotes.forEach { extractedQuote ->
        val isQuotedPostInTheSameThread = (extractedQuote.postDescriptor.boardCode == threadDescriptor.boardCode
          && extractedQuote.postDescriptor.threadNo == threadDescriptor.threadNo)

        if (!isQuotedPostInTheSameThread) {
          // Cross-thread reply or something like that, we don't support it since it shouldn't
          // be used normally. The only use case that come to mind is when there are two
          // different threads bookmarked and someone from one bookmarked thread replied to our
          // post in another bookmarked thread. Normally, nobody would expect for this to work
          // and that's why we don't support it.
          return@forEach
        }

        val repliesToMyPostSet = quoteOwnerPostsMap.getOrPut(
          key = extractedQuote.postDescriptor,
          defaultValue = { mutableSetWithCap(16) }
        )

        repliesToMyPostSet += ReplyToMyPost(
          postDescriptor = simplePostObject.postDescriptor(),
          commentRaw = simplePostObject.comment()
        )
      }
    }

    if (quoteOwnerPostsMap.isEmpty()) {
      return emptyMap()
    }

    val myPostsInThread = kurobaExLiteDatabase.call {
      return@call markedPostDao.selectWithType(
        siteKey = threadDescriptor.siteKeyActual,
        boardCode = threadDescriptor.boardCode,
        threadNo = threadDescriptor.threadNo,
        type = MarkedPostType.MyPost.type
      ).mapNotNull { markedPostEntity ->
        val markedPostType = MarkedPostType.fromTypRaw(markedPostEntity.type)
          ?: return@mapNotNull null

        return@mapNotNull MarkedPost(
          postDescriptor = markedPostEntity.postKey.postDescriptor,
          markedPostType = markedPostType
        )
      }
    }
      .onFailure { error ->
        logcatError(TAG) {
          "savedReplyRepository.preloadForThread($threadDescriptor) " +
            "error: ${error.asLogIfImportantOrErrorMessage()}"
        }
      }
      .getOrNull() ?: emptyList()

    if (myPostsInThread.isEmpty()) {
      return emptyMap()
    }

    val quotesToMeInThreadMap = retainSavedPostNoMap(
      myPostsInThread = myPostsInThread,
      quoteOwnerPostsMap = quoteOwnerPostsMap
    )

    if (quotesToMeInThreadMap.isEmpty()) {
      return emptyMap()
    }

    val quotePostDescriptorsMap =
      mutableMapWithCap<PostDescriptor, MutableList<ReplyToMyPost>>(quotesToMeInThreadMap.size)

    quotesToMeInThreadMap.forEach { (myPostDescriptor, repliesToMeSet) ->
      val listOfReplies = quotePostDescriptorsMap.getOrPut(
        key = myPostDescriptor,
        defaultValue = { mutableListWithCap(repliesToMeSet.size) }
      )

      repliesToMeSet.forEach { tempReplyToMyPost ->
        val replyToMyPost = ReplyToMyPost(
          postDescriptor = PostDescriptor.create(
            threadDescriptor = threadDescriptor,
            postNo = tempReplyToMyPost.postDescriptor.postNo,
            postSubNo = tempReplyToMyPost.postDescriptor.postSubNo
          ),
          commentRaw = tempReplyToMyPost.commentRaw
        )

        listOfReplies.add(replyToMyPost)
      }
    }

    return quotePostDescriptorsMap
  }

  private fun retainSavedPostNoMap(
    myPostsInThread: List<MarkedPost>,
    quoteOwnerPostsMap: Map<PostDescriptor, Set<ReplyToMyPost>>
  ): Map<PostDescriptor, MutableSet<ReplyToMyPost>> {
    val resultMap: MutableMap<PostDescriptor, MutableSet<ReplyToMyPost>> = mutableMapWithCap(16)

    val myPostDescriptorsSet = myPostsInThread
      .map { chanSavedReply -> chanSavedReply.postDescriptor }
      .toSet()

    for ((quotePostNo, tempReplies) in quoteOwnerPostsMap) {
      for (tempReply in tempReplies) {
        if (!myPostDescriptorsSet.contains(quotePostNo)) {
          continue
        }

        resultMap.getOrPut(
          key = quotePostNo,
          defaultValue = { mutableSetOf() }
        ).add(tempReply)
      }
    }

    return resultMap
  }


  class ReplyToMyPost(
    val postDescriptor: PostDescriptor,
    val commentRaw: String
  ) {

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as ReplyToMyPost

      if (postDescriptor != other.postDescriptor) return false

      return true
    }

    override fun hashCode(): Int {
      return postDescriptor.hashCode()
    }

    override fun toString(): String {
      return "ReplyToMyPost(postDescriptor=$postDescriptor, commentRaw='${commentRaw.take(50)}')"
    }
  }

  companion object {
    private const val TAG = "ParsePostReplies"
    private const val BATCH_SIZE = 8
  }
}