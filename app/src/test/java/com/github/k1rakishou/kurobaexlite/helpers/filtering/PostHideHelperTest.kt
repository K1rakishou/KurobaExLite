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
            state = ChanPostHide.State.HiddenManually
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
  fun `Post must be hidden if it replies to at least one hidden post which is hidden manually`() {
    runTest {
      val postHideHelper = PostHideHelper(
        postHideRepository = postHideRepository,
        postReplyChainRepository = postReplyChainRepository
      )

      val postCreator = PostCreator(threadDescriptor)
      val posts = postCreator.createPosts(3, includeOP = false)
      var firstPost = posts[0]
      var secondPost = posts[1]
      var thirdPost = posts[2]

      kotlin.run {
        val testChanPostHide = mapOf(
          firstPost.postDescriptor to ChanPostHide(
            postDescriptor = firstPost.postDescriptor,
            applyToReplies = true,
            state = ChanPostHide.State.HiddenManually
          )
        )

        coEvery { postHideRepository.postHidesForChanDescriptor(threadDescriptor) } returns testChanPostHide
      }

      /**
       *  Reply direction: from right to left
       *  <----------------------------------+
       *          Post2
       *        /
       *  Post1
       *        \
       *          Post3
       * */
      kotlin.run {
        postReplyChainRepository.insertRepliesFrom(
          firstPost.postDescriptor,
          setOf(secondPost.postDescriptor, thirdPost.postDescriptor)
        )
      }

      val (resultPosts, toHide, toUnhide) = postHideHelper.processPosts(
        postCreator.chanDescriptor,
        posts
      )

      assertEquals(3, resultPosts.size)
      assertEquals(3, toHide.size)
      assertEquals(0, toUnhide.size)

      firstPost = resultPosts[0]
      secondPost = resultPosts[1]
      thirdPost = resultPosts[2]

      val chanPostHide1 = toHide[0]
      val chanPostHide2 = toHide[1]
      val chanPostHide3 = toHide[2]

      kotlin.run {
        val postHideUi = firstPost.postHideUi!!
        val postDescriptor = firstPost.postDescriptor

        assertEquals("Post (${postDescriptor.postNo}) hidden manually", postHideUi.reason)
        assertEquals(0, chanPostHide1.repliesToHiddenPostsCount())
      }

      kotlin.run {
        val postHideUi = secondPost.postHideUi!!
        val postDescriptor = secondPost.postDescriptor
        val repliesToHiddenPostsCount = chanPostHide2.repliesToHiddenPostsCount()

        assertEquals(
          "Post (${postDescriptor.postNo}) hidden because it replies to ${repliesToHiddenPostsCount} hidden post(s)",
          postHideUi.reason
        )

        assertEquals(1, chanPostHide2.repliesToHiddenPostsCount())
        assertTrue(chanPostHide2.repliesToHiddenPostsContain(firstPost.postDescriptor))
      }

      kotlin.run {
        val postHideUi = thirdPost.postHideUi!!
        val postDescriptor = thirdPost.postDescriptor

        assertEquals(
          "Post (${postDescriptor.postNo}) hidden because it replies to 1 hidden post(s)",
          postHideUi.reason
        )

        assertEquals(1, chanPostHide3.repliesToHiddenPostsCount())
        assertTrue(chanPostHide3.repliesToHiddenPostsContain(firstPost.postDescriptor))
      }
    }
  }

  @Test
  fun `Post must be hidden if it replies to at least one post that replies to another post which is hidden manually`() {
    runTest {
      val postHideHelper = PostHideHelper(
        postHideRepository = postHideRepository,
        postReplyChainRepository = postReplyChainRepository
      )

      val postCreator = PostCreator(threadDescriptor)
      val posts = postCreator.createPosts(4, includeOP = false)
      var firstPost = posts[0]
      var secondPost = posts[1]
      var thirdPost = posts[2]
      var fourthPost = posts[3]

      kotlin.run {
        val testChanPostHide = mapOf(
          firstPost.postDescriptor to ChanPostHide(
            postDescriptor = firstPost.postDescriptor,
            applyToReplies = true,
            state = ChanPostHide.State.HiddenManually,
          ),
          secondPost.postDescriptor to ChanPostHide(
            postDescriptor = secondPost.postDescriptor,
            applyToReplies = true,
            state = ChanPostHide.State.Unspecified,
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
      assertEquals(4, toHide.size)
      assertEquals(0, toUnhide.size)

      firstPost = resultPosts[0]
      secondPost = resultPosts[1]
      thirdPost = resultPosts[2]
      fourthPost = resultPosts[3]

      val chanPostHide1 = toHide.first { chanPostHide -> chanPostHide.postDescriptor == firstPost.postDescriptor }
      val chanPostHide2 = toHide.first { chanPostHide -> chanPostHide.postDescriptor == secondPost.postDescriptor }
      val chanPostHide3 = toHide.first { chanPostHide -> chanPostHide.postDescriptor == thirdPost.postDescriptor }
      val chanPostHide4 = toHide.first { chanPostHide -> chanPostHide.postDescriptor == fourthPost.postDescriptor }

      kotlin.run {
        val postHideUi = firstPost.postHideUi!!
        val postDescriptor = firstPost.postDescriptor

        assertEquals("Post (${postDescriptor.postNo}) hidden manually", postHideUi.reason)
        assertEquals(0, chanPostHide1.repliesToHiddenPostsCount())
      }

      kotlin.run {
        val postHideUi = secondPost.postHideUi!!
        val postDescriptor = secondPost.postDescriptor

        assertEquals(
          "Post (${postDescriptor.postNo}) hidden because it replies to 1 hidden post(s)",
          postHideUi.reason
        )

        assertEquals(1, chanPostHide2.repliesToHiddenPostsCount())
        assertTrue(chanPostHide2.repliesToHiddenPostsContain(firstPost.postDescriptor))
      }

      kotlin.run {
        val postHideUi = thirdPost.postHideUi!!
        val postDescriptor = thirdPost.postDescriptor

        assertEquals(
          "Post (${postDescriptor.postNo}) hidden because it replies to 1 hidden post(s)",
          postHideUi.reason
        )

        assertEquals(1, chanPostHide3.repliesToHiddenPostsCount())
        assertTrue(chanPostHide3.repliesToHiddenPostsContain(secondPost.postDescriptor))
      }

      kotlin.run {
        val postHideUi = fourthPost.postHideUi!!
        val postDescriptor = fourthPost.postDescriptor

        assertEquals(
          "Post (${postDescriptor.postNo}) hidden because it replies to 1 hidden post(s)",
          postHideUi.reason
        )

        assertEquals(1, chanPostHide3.repliesToHiddenPostsCount())
        assertTrue(chanPostHide4.repliesToHiddenPostsContain(secondPost.postDescriptor))
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
            state = ChanPostHide.State.HiddenManually
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

  @Test
  fun `Should not end up in endless recursion when 2 posts reply to each other`() {
    runTest {
      val postHideHelper = PostHideHelper(
        postHideRepository = postHideRepository,
        postReplyChainRepository = postReplyChainRepository
      )

      val postCreator = PostCreator(threadDescriptor)
      val posts = postCreator.createPosts(3)
      val secondPost = posts[1]
      val thirdPost = posts[2]

      kotlin.run {
        val testChanPostHide = mapOf(
          secondPost.postDescriptor to ChanPostHide(
            postDescriptor = secondPost.postDescriptor,
            applyToReplies = true,
            state = ChanPostHide.State.HiddenManually
          )
        )

        coEvery { postHideRepository.postHidesForChanDescriptor(threadDescriptor) } returns testChanPostHide
      }

      /**
       *  Reply direction: from right to left
       *  <----------------------------------+
       *  Post2 >-< Post3
       * */
      kotlin.run {
        postReplyChainRepository.insertRepliesFrom(
          thirdPost.postDescriptor,
          setOf(secondPost.postDescriptor)
        )

        postReplyChainRepository.insertRepliesFrom(
          secondPost.postDescriptor,
          setOf(thirdPost.postDescriptor)
        )
      }

      val (resultPosts, toHide, toUnhide) = postHideHelper.processPosts(
        postCreator.chanDescriptor,
        posts
      )

      assertEquals(3, resultPosts.size)
      assertEquals(2, toHide.size)
      assertEquals(0, toUnhide.size)
    }
  }

  @Test
  fun `Should be able to unhide hidden posts`() {
    runTest {
      repeat(10) {
        val postHideHelper = PostHideHelper(
          postHideRepository = postHideRepository,
          postReplyChainRepository = postReplyChainRepository
        )

        val postCreator = PostCreator(threadDescriptor)
        val posts = postCreator.createPosts(5)
        val firstPost = posts[0]
        val secondPost = posts[1]
        val thirdPost = posts[2]
        val fourthPost = posts[3]
        val fifthPost = posts[4]

        kotlin.run {
          coEvery { postHideRepository.postHidesForChanDescriptor(threadDescriptor) } returns emptyMap()
        }

        /**
         *  Reply direction: from right to left
         *  <----------------------------------+
         *        +----< Post2 <----+
         *        |                 |
         *  Post1 +----< Post3 <----+-----< Post5
         *        |                 |
         *        +----< Post4 <----+
         * */
        kotlin.run {
          postReplyChainRepository.insertRepliesFrom(firstPost.postDescriptor, setOf(secondPost.postDescriptor))
          postReplyChainRepository.insertRepliesFrom(firstPost.postDescriptor, setOf(thirdPost.postDescriptor))
          postReplyChainRepository.insertRepliesFrom(firstPost.postDescriptor, setOf(fourthPost.postDescriptor))
          postReplyChainRepository.insertRepliesFrom(secondPost.postDescriptor, setOf(fifthPost.postDescriptor))
          postReplyChainRepository.insertRepliesFrom(thirdPost.postDescriptor, setOf(fifthPost.postDescriptor))
          postReplyChainRepository.insertRepliesFrom(fourthPost.postDescriptor, setOf(fifthPost.postDescriptor))
          postReplyChainRepository.insertRepliesFrom(fifthPost.postDescriptor, setOf())
        }

        var postProcessResult = postHideHelper.processPosts(postCreator.chanDescriptor, posts)
        var resultPosts = postProcessResult.posts
        var toHide = postProcessResult.toHide
        var toUnhide = postProcessResult.toUnhide

        assertEquals(5, resultPosts.size)
        assertEquals(0, toHide.size)
        assertEquals(0, toUnhide.size)

        // Hide the 2nd post. This should hide the 5th post as well since "applyToReplies == true"
        kotlin.run {
          val testChanPostHide = mapOf(
            secondPost.postDescriptor to ChanPostHide(
              postDescriptor = secondPost.postDescriptor,
              applyToReplies = true,
              state = ChanPostHide.State.HiddenManually
            )
          )

          coEvery { postHideRepository.postHidesForChanDescriptor(threadDescriptor) } returns testChanPostHide
        }

        postProcessResult = postHideHelper.processPosts(
          postCreator.chanDescriptor,
          resultPosts
        )

        resultPosts = postProcessResult.posts
        toHide = postProcessResult.toHide
        toUnhide = postProcessResult.toUnhide

        assertEquals(5, resultPosts.size)
        assertEquals(2, toHide.size)
        assertEquals(0, toUnhide.size)
        assertEquals(2, resultPosts.count { postCellData -> postCellData.postHideUi != null })

        toHide.forEach { chanPostHide ->
          if (chanPostHide.postDescriptor.postNo == 5L) {
            assertEquals(ChanPostHide.State.Unspecified, chanPostHide.state)
          } else {
            assertEquals(ChanPostHide.State.HiddenManually, chanPostHide.state)
          }
        }

        assertNull(resultPosts[0].postHideUi)
        assertNull(resultPosts[2].postHideUi)
        assertNull(resultPosts[3].postHideUi)

        assertEquals("Post (2) hidden manually", resultPosts[1].postHideUi?.reason)
        assertEquals("Post (5) hidden because it replies to 1 hidden post(s)", resultPosts[4].postHideUi?.reason)

        // Hide 2nd, 3rd and 4th posts. This should not change the 5th post.
        kotlin.run {
          val testChanPostHide = mapOf(
            secondPost.postDescriptor to ChanPostHide(
              postDescriptor = secondPost.postDescriptor,
              applyToReplies = true,
              state = ChanPostHide.State.HiddenManually
            ),
            thirdPost.postDescriptor to ChanPostHide(
              postDescriptor = thirdPost.postDescriptor,
              applyToReplies = true,
              state = ChanPostHide.State.HiddenManually
            ),
            fourthPost.postDescriptor to ChanPostHide(
              postDescriptor = fourthPost.postDescriptor,
              applyToReplies = true,
              state = ChanPostHide.State.HiddenManually
            )
          )

          coEvery { postHideRepository.postHidesForChanDescriptor(threadDescriptor) } returns testChanPostHide
        }

        postProcessResult = postHideHelper.processPosts(
          postCreator.chanDescriptor,
          resultPosts
        )

        resultPosts = postProcessResult.posts
        toHide = postProcessResult.toHide
        toUnhide = postProcessResult.toUnhide

        assertEquals(5, resultPosts.size)
        // toHide does not contain the 2nd post here because it was already hidden on the previous step
        assertEquals(3, toHide.size)
        assertEquals(0, toUnhide.size)
        assertEquals(4, resultPosts.count { postCellData -> postCellData.postHideUi != null })

        toHide.forEach { chanPostHide ->
          if (chanPostHide.postDescriptor.postNo == 5L) {
            assertEquals(ChanPostHide.State.Unspecified, chanPostHide.state)
          } else {
            assertEquals(ChanPostHide.State.HiddenManually, chanPostHide.state)
          }
        }

        assertNull(resultPosts[0].postHideUi)
        assertEquals("Post (2) hidden manually", resultPosts[1].postHideUi?.reason)
        assertEquals("Post (3) hidden manually", resultPosts[2].postHideUi?.reason)
        assertEquals("Post (4) hidden manually", resultPosts[3].postHideUi?.reason)
        assertEquals("Post (5) hidden because it replies to 3 hidden post(s)", resultPosts[4].postHideUi?.reason)

        // Unhide 2nd and 4th posts. The 5th post should still remain hidden.
        kotlin.run {
          val testChanPostHide = mapOf(
            secondPost.postDescriptor to ChanPostHide(
              postDescriptor = secondPost.postDescriptor,
              applyToReplies = true,
              state = ChanPostHide.State.UnhiddenManually
            ),
            thirdPost.postDescriptor to ChanPostHide(
              postDescriptor = thirdPost.postDescriptor,
              applyToReplies = true,
              state = ChanPostHide.State.HiddenManually
            ),
            fourthPost.postDescriptor to ChanPostHide(
              postDescriptor = fourthPost.postDescriptor,
              applyToReplies = true,
              state = ChanPostHide.State.UnhiddenManually
            ),
            fifthPost.postDescriptor to ChanPostHide(
              postDescriptor = fifthPost.postDescriptor,
              applyToReplies = true,
              repliesToHiddenPosts = setOf(
                secondPost.postDescriptor,
                thirdPost.postDescriptor,
                fourthPost.postDescriptor
              )
            )
          )

          coEvery { postHideRepository.postHidesForChanDescriptor(threadDescriptor) } returns testChanPostHide
        }

        postProcessResult = postHideHelper.processPosts(
          postCreator.chanDescriptor,
          resultPosts
        )

        resultPosts = postProcessResult.posts
        toHide = postProcessResult.toHide
        toUnhide = postProcessResult.toUnhide

        assertEquals(5, resultPosts.size)
        assertEquals(1, toHide.size)
        assertEquals(2, toUnhide.size)
        assertEquals(2, resultPosts.count { postCellData -> postCellData.postHideUi != null })

        toHide.forEach { chanPostHide ->
          if (chanPostHide.postDescriptor.postNo == 5L) {
            assertEquals(ChanPostHide.State.Unspecified, chanPostHide.state)
          } else {
            assertEquals(ChanPostHide.State.HiddenManually, chanPostHide.state)
          }
        }

        assertNull(resultPosts[0].postHideUi)
        assertNull(resultPosts[1].postHideUi)
        assertEquals("Post (3) hidden manually", resultPosts[2].postHideUi?.reason)
        assertNull(resultPosts[3].postHideUi)
        assertEquals("Post (5) hidden because it replies to 1 hidden post(s)", resultPosts[4].postHideUi?.reason)

        // Unhide the last 3rd post. This 5th post should get unhidden as well.
        kotlin.run {
          val testChanPostHide = mapOf(
            secondPost.postDescriptor to ChanPostHide(
              postDescriptor = secondPost.postDescriptor,
              applyToReplies = true,
              state = ChanPostHide.State.UnhiddenManually
            ),
            thirdPost.postDescriptor to ChanPostHide(
              postDescriptor = thirdPost.postDescriptor,
              applyToReplies = true,
              state = ChanPostHide.State.UnhiddenManually
            ),
            fourthPost.postDescriptor to ChanPostHide(
              postDescriptor = fourthPost.postDescriptor,
              applyToReplies = true,
              state = ChanPostHide.State.UnhiddenManually
            ),
            fifthPost.postDescriptor to ChanPostHide(
              postDescriptor = fifthPost.postDescriptor,
              applyToReplies = true,
              state = ChanPostHide.State.UnhiddenManually,
              repliesToHiddenPosts = setOf(
                secondPost.postDescriptor,
                thirdPost.postDescriptor,
                fourthPost.postDescriptor
              )
            )
          )

          coEvery { postHideRepository.postHidesForChanDescriptor(threadDescriptor) } returns testChanPostHide
        }

        postProcessResult = postHideHelper.processPosts(
          postCreator.chanDescriptor,
          resultPosts
        )

        resultPosts = postProcessResult.posts
        toHide = postProcessResult.toHide
        toUnhide = postProcessResult.toUnhide

        assertEquals(5, resultPosts.size)
        assertEquals(0, toHide.size)
        assertEquals(2, toUnhide.size)
        assertEquals(0, resultPosts.count { postCellData -> postCellData.postHideUi != null })
      }
    }
  }

  private class PostCreator(
    val chanDescriptor: ChanDescriptor
  ) {

    fun createPosts(count: Int, includeOP: Boolean = true): List<PostCellData> {
      val threadDescriptor = if (chanDescriptor is ThreadDescriptor) {
        chanDescriptor
      } else {
        ThreadDescriptor(chanDescriptor as CatalogDescriptor, 1)
      }

      var postNo = if (includeOP) 1L else 2L

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