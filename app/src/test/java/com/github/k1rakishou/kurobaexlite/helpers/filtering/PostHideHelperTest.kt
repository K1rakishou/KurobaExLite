package com.github.k1rakishou.kurobaexlite.helpers.filtering

import com.github.k1rakishou.kurobaexlite.model.data.local.ChanPostHide
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.model.repository.IPostHideRepository
import com.github.k1rakishou.kurobaexlite.model.repository.IPostReplyChainRepository
import com.github.k1rakishou.kurobaexlite.model.repository.PostReplyChainRepository
import com.github.k1rakishou.kurobaexlite.sites.chan4.Chan4
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostHideHelperTest {
  private lateinit var postHideRepository: IPostHideRepository
  private lateinit var postReplyChainRepository: IPostReplyChainRepository

  private val catalogDescriptor = CatalogDescriptor(Chan4.SITE_KEY, "test")
  private val threadDescriptor = ThreadDescriptor.create(catalogDescriptor, 1L)

  @Before
  fun setup() {
    postHideRepository = mockk<IPostHideRepository>()
    postReplyChainRepository = PostReplyChainRepository()
  }

  @Test
  fun `Post must not be hidden if there are no ChanPostHide associated with it`() {
    runTest {
      val postHideHelper = PostHideHelper(
        postHideRepository = postHideRepository,
        postReplyChainRepository = postReplyChainRepository
      )

      val postCreator = PostCreator(threadDescriptor)
      val posts = postCreator.createPosts(1)

      coEvery { postHideRepository.postHidesForChanDescriptor(threadDescriptor) } returns emptyMap()

      val (resultPosts, toHide, toUnhide) = postHideHelper.processPosts(
        postCreator.chanDescriptor,
        posts
      )

      assertEquals(1, resultPosts.size)
      assertEquals(0, toHide.size)
      assertEquals(0, toUnhide.size)

      assertNull(resultPosts.first().postHideUi)
    }
  }

  @Test
  fun `Post must be hidden if there is a ChanPostHide associated with it`() {
    runTest {
      val postHideHelper = PostHideHelper(
        postHideRepository = postHideRepository,
        postReplyChainRepository = postReplyChainRepository
      )

      val postCreator = PostCreator(threadDescriptor)
      val posts = postCreator.createPosts(2)
      val firstPost = posts[0]
      val secondPost = posts[1]

      kotlin.run {
        val testChanPostHide = mapOf(
          secondPost.postDescriptor to ChanPostHide(
            postDescriptor = secondPost.postDescriptor,
            applyToReplies = true,
            hiddenManually = true
          )
        )

        coEvery { postHideRepository.postHidesForChanDescriptor(threadDescriptor) } returns testChanPostHide
      }

      kotlin.run {
        postReplyChainRepository.insertRepliesFrom(
          firstPost.postDescriptor,
          setOf(secondPost.postDescriptor)
        )
      }

      val (resultPosts, toHide, toUnhide) = postHideHelper.processPosts(
        postCreator.chanDescriptor,
        posts
      )

      assertEquals(2, resultPosts.size)

      resultPosts[0].let { firstPost ->
        assertNull(firstPost.postHideUi)
      }
      resultPosts[1].let { secondsPost ->
        assertNotNull(secondsPost.postHideUi)

        kotlin.run {
          val postHideUi = secondsPost.postHideUi!!
          assertEquals("Post (${secondsPost.postDescriptor.postNo}) hidden manually", postHideUi.reason)
        }
      }

      assertEquals(1, toHide.size)
      assertEquals(0, toUnhide.size)
    }
  }

  @Test
  fun `Post must be hidden if it replies to at least one hidden post`() {
    runTest {
      val postHideHelper = PostHideHelper(
        postHideRepository = postHideRepository,
        postReplyChainRepository = postReplyChainRepository
      )

      val postCreator = PostCreator(threadDescriptor)
      val posts = postCreator.createPosts(4)
      val firstPost = posts[0]
      var secondPost = posts[1]
      var thirdPost = posts[2]
      var fourthPost = posts[3]

      kotlin.run {
        val testChanPostHide = mapOf(
          secondPost.postDescriptor to ChanPostHide(
            postDescriptor = secondPost.postDescriptor,
            applyToReplies = true,
            hiddenManually = true,
            repliesToHiddenPosts = setOf(firstPost.postDescriptor)
          )
        )

        coEvery { postHideRepository.postHidesForChanDescriptor(threadDescriptor) } returns testChanPostHide
      }

      /**
       *  Reply direction: from right to left
       *  <----------------------------------+
       *                   Post3
       *                 /
       *  Post1 <- Post2
       *                 \
       *                   Post4
       * */
      kotlin.run {
        postReplyChainRepository.insertRepliesFrom(
          firstPost.postDescriptor,
          setOf(secondPost.postDescriptor)
        )

        postReplyChainRepository.insertRepliesFrom(
          secondPost.postDescriptor,
          setOf(thirdPost.postDescriptor, fourthPost.postDescriptor)
        )
      }

      val (resultPosts, toHide, toUnhide) = postHideHelper.processPosts(
        postCreator.chanDescriptor,
        posts
      )

      assertEquals(4, resultPosts.size)
      assertEquals(3, toHide.size)
      assertEquals(0, toUnhide.size)

      secondPost = resultPosts[1]
      thirdPost = resultPosts[2]
      fourthPost = resultPosts[3]

      val chanPostHide1 = toHide[0]
      val chanPostHide2 = toHide[1]
      val chanPostHide3 = toHide[2]

      kotlin.run {
        assertNull(firstPost.postHideUi)
      }

      kotlin.run {
        val postHideUi = secondPost.postHideUi!!
        val postDescriptor = secondPost.postDescriptor

        assertEquals("Post (${postDescriptor.postNo}) hidden manually", postHideUi.reason)
        assertEquals(1, chanPostHide1.repliesToHiddenPostsCount())
        assertTrue(chanPostHide1.repliesToHiddenPostsContain(firstPost.postDescriptor))
      }

      kotlin.run {
        val postHideUi = thirdPost.postHideUi!!
        val postDescriptor = thirdPost.postDescriptor
        val repliesToHiddenPostsCount = chanPostHide2.repliesToHiddenPostsCount()

        assertEquals(
          "Post (${postDescriptor.postNo}) hidden because it replies to ${repliesToHiddenPostsCount} hidden post(s)",
          postHideUi.reason
        )

        assertEquals(1, chanPostHide2.repliesToHiddenPostsCount())
        assertTrue(chanPostHide2.repliesToHiddenPostsContain(secondPost.postDescriptor))
      }

      kotlin.run {
        val postHideUi = fourthPost.postHideUi!!
        val postDescriptor = fourthPost.postDescriptor

        assertEquals(
          "Post (${postDescriptor.postNo}) hidden because it replies to 1 hidden post(s)",
          postHideUi.reason
        )

        assertEquals(1, chanPostHide3.repliesToHiddenPostsCount())
        assertTrue(chanPostHide3.repliesToHiddenPostsContain(secondPost.postDescriptor))
      }
    }
  }

  @Test
  fun `Should be able to hide the whole diamond shaped reply chain when hiding the head post`() {
    runTest {
      val postHideHelper = PostHideHelper(
        postHideRepository = postHideRepository,
        postReplyChainRepository = postReplyChainRepository
      )

      val postCreator = PostCreator(threadDescriptor)
      val posts = postCreator.createPosts(5)

      var firstPost = posts[0]
      var secondPost = posts[1]
      var thirdPost = posts[2]
      var fourthPost = posts[3]
      var fifthPost = posts[4]

      kotlin.run {
        val testChanPostHide = mapOf(
          secondPost.postDescriptor to ChanPostHide(
            postDescriptor = secondPost.postDescriptor,
            applyToReplies = true,
            hiddenManually = true
          )
        )

        coEvery { postHideRepository.postHidesForChanDescriptor(threadDescriptor) } returns testChanPostHide
      }

      /**
       *  Reply direction: from right to left
       *  <----------------------------------+
       *                  Post3
       *                /       \
       *  Post1 - Post2          Post5
       *                \       /
       *                  Post4
       * */
      kotlin.run {
        postReplyChainRepository.insertRepliesFrom(
          firstPost.postDescriptor,
          setOf(secondPost.postDescriptor)
        )

        postReplyChainRepository.insertRepliesFrom(
          secondPost.postDescriptor,
          setOf(thirdPost.postDescriptor, fourthPost.postDescriptor)
        )

        postReplyChainRepository.insertRepliesFrom(
          thirdPost.postDescriptor,
          setOf(fifthPost.postDescriptor)
        )

        postReplyChainRepository.insertRepliesFrom(
          fourthPost.postDescriptor,
          setOf(fifthPost.postDescriptor)
        )

        postReplyChainRepository.insertRepliesFrom(
          fifthPost.postDescriptor,
          setOf()
        )
      }

      val (resultPosts, toHide, toUnhide) = postHideHelper.processPosts(
        postCreator.chanDescriptor,
        posts
      )

      assertEquals(5, resultPosts.size)
      assertEquals(4, toHide.size)
      assertEquals(0, toUnhide.size)

      firstPost = resultPosts[0]
      secondPost = resultPosts[1]
      thirdPost = resultPosts[2]
      fourthPost = resultPosts[3]
      fifthPost = resultPosts[4]

      val chanPostHide1 = toHide[0]
      val chanPostHide2 = toHide[1]
      val chanPostHide3 = toHide[2]
      val chanPostHide4 = toHide[3]

      kotlin.run {
        assertNull(firstPost.postHideUi)
      }

      kotlin.run {
        val postHideUi = secondPost.postHideUi!!
        val postDescriptor = secondPost.postDescriptor

        assertEquals("Post (${postDescriptor.postNo}) hidden manually", postHideUi.reason)
        assertEquals(0, chanPostHide1.repliesToHiddenPostsCount())
      }

      kotlin.run {
        val postHideUi = thirdPost.postHideUi!!
        val postDescriptor = thirdPost.postDescriptor

        assertEquals(
          "Post (${postDescriptor.postNo}) hidden because it replies to 1 hidden post(s)",
          postHideUi.reason
        )

        assertEquals(1, chanPostHide2.repliesToHiddenPostsCount())
        assertTrue(chanPostHide2.repliesToHiddenPostsContain(secondPost.postDescriptor))
      }

      kotlin.run {
        val postHideUi = fourthPost.postHideUi!!
        val postDescriptor = fourthPost.postDescriptor

        assertEquals(
          "Post (${postDescriptor.postNo}) hidden because it replies to 1 hidden post(s)",
          postHideUi.reason
        )

        assertEquals(1, chanPostHide3.repliesToHiddenPostsCount())
        assertTrue(chanPostHide3.repliesToHiddenPostsContain(secondPost.postDescriptor))
      }

      kotlin.run {
        val postHideUi = fifthPost.postHideUi!!
        val postDescriptor = fifthPost.postDescriptor

        assertEquals(
          "Post (${postDescriptor.postNo}) hidden because it replies to 2 hidden post(s)",
          postHideUi.reason
        )

        assertEquals(2, chanPostHide4.repliesToHiddenPostsCount())
        assertTrue(chanPostHide4.repliesToHiddenPostsContain(thirdPost.postDescriptor))
        assertTrue(chanPostHide4.repliesToHiddenPostsContain(fourthPost.postDescriptor))
      }
    }
  }

  private class PostCreator(
    val chanDescriptor: ChanDescriptor
  ) {

    fun createPosts(count: Int): List<PostCellData> {
      val threadDescriptor = if (chanDescriptor is ThreadDescriptor) {
        chanDescriptor
      } else {
        ThreadDescriptor(chanDescriptor as CatalogDescriptor, 1)
      }

      var postNo = 1L

      return (0 until count).map { index ->
        val currentPostNo = postNo++

        return@map PostCellData(
          originalPostOrder = index,
          chanDescriptor = chanDescriptor,
          postDescriptor = PostDescriptor(threadDescriptor = threadDescriptor, postNo = currentPostNo),
          postSubjectUnparsed = "",
          postCommentUnparsed = "${currentPostNo}",
          timeMs = null,
          opMark = false,
          sage = false,
          name = null,
          tripcode = null,
          posterId = null,
          countryFlag = null,
          boardFlag = null,
          images = null,
          threadRepliesTotal = null,
          threadImagesTotal = null,
          threadPostersTotal = null,
          lastModified = null,
          archived = false,
          deleted = false,
          closed = false,
          sticky = null,
          postHideUi = null,
          bumpLimit = null,
          imageLimit = null,
          parsedPostData = null,
          postServerDataHashForListAnimations = null,
        )
      }
    }
  }

}