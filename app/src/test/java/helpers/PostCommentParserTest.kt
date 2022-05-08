package helpers

import com.github.k1rakishou.kurobaexlite.helpers.parser.Chan4PostParser
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentParser
import com.github.k1rakishou.kurobaexlite.managers.ISiteManager
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import com.github.k1rakishou.kurobaexlite.sites.Site
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase
import org.junit.Test

class PostCommentParserTest : TestCase() {
  private val chan4PostParser = Chan4PostParser()
  private val fakeSiteKey = SiteKey("test")

  private lateinit var fakeSiteManager: ISiteManager

  override fun setUp() {
    fakeSiteManager = mockk<ISiteManager>()
    val fakeSite = mockk<Site>()

    every { fakeSiteManager.bySiteKey(fakeSiteKey) } returns fakeSite
    every { fakeSite.parser() } returns chan4PostParser
  }

  @Test
  fun testBrTag() {
    val postCommentParser = PostCommentParser(fakeSiteManager)
    val commentRaw = "Test <br> 123"

    val postData = createPostDataWithComment(commentRaw)
    val textParts = postCommentParser.parsePostComment(
      postData.postCommentUnparsed,
      postData.postDescriptor
    )

    assertEquals(3, textParts.size)
    assertEquals("Test ", textParts[0].text)
    assertEquals("\n", textParts[1].text)
    assertEquals(" 123", textParts[2].text)
  }

  @Test
  fun testWbrTag() {
    val postCommentParser = PostCommentParser(fakeSiteManager)
    val commentRaw = "Test <wbr> 123"

    val postData = createPostDataWithComment(commentRaw)
    val textParts = postCommentParser.parsePostComment(
      postData.postCommentUnparsed,
      postData.postDescriptor
    )

    assertEquals(1, textParts.size)
    assertEquals("Test  123", textParts[0].text)
  }

  @Test
  fun testSpanTag() {
    val postCommentParser = PostCommentParser(fakeSiteManager)
    val commentRaw = "<span class=\\\"quote\\\">&gt;Tomo it&#039;s you! YOU&#039;RE the Azumanga Daioh!</span>"

    val postData = createPostDataWithComment(commentRaw)
    val textParts = postCommentParser.parsePostComment(
      postData.postCommentUnparsed,
      postData.postDescriptor
    )

    assertEquals(1, textParts.size)
    val textPart = textParts.first()

    assertEquals(">Tomo it's you! YOU'RE the Azumanga Daioh!", textPart.text)
  }

  private fun createPostDataWithComment(comment: String): PostData {
    return PostData(
      originalPostOrder = 0,
      postDescriptor = PostDescriptor.create(fakeSiteKey, "test", 1122, 1123),
      postSubjectUnparsed = "",
      postCommentUnparsed = comment,
      images = null,
      timeMs = System.currentTimeMillis(),
      threadRepliesTotal = 0,
      threadImagesTotal = 0,
      threadPostersTotal = 0,
      lastModified = null,
      archived = null,
      closed = null,
      sticky = null,
      bumpLimit = null,
      imageLimit = null,
    )
  }

}