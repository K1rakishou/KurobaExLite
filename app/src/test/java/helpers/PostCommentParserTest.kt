package helpers

import com.github.k1rakishou.kurobaexlite.helpers.PostCommentParser
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import junit.framework.TestCase
import org.junit.Test

class PostCommentParserTest : TestCase() {

  @Test
  fun testBrTag() {
    val postCommentParser = PostCommentParser()
    val commentRaw = "Test <br> 123"

    val postData = createPostDataWithComment(commentRaw)
    val textParts = postCommentParser.parsePostCommentInternal(
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
    val postCommentParser = PostCommentParser()
    val commentRaw = "Test <wbr> 123"

    val postData = createPostDataWithComment(commentRaw)
    val textParts = postCommentParser.parsePostCommentInternal(
      postData.postCommentUnparsed,
      postData.postDescriptor
    )

    assertEquals(2, textParts.size)
    assertEquals("Test ", textParts[0].text)
    assertEquals(" 123", textParts[1].text)
  }

  @Test
  fun testSpanTag() {
    val postCommentParser = PostCommentParser()
    val commentRaw = "<span class=\\\"quote\\\">&gt;Tomo it&#039;s you! YOU&#039;RE the Azumanga Daioh!</span>"

    val postData = createPostDataWithComment(commentRaw)
    val textParts = postCommentParser.parsePostCommentInternal(
      postData.postCommentUnparsed,
      postData.postDescriptor
    )

    assertEquals(1, textParts.size)
    val textPart = textParts.first()

    assertEquals(">Tomo it's you! YOU'RE the Azumanga Daioh!", textPart.text)
  }

  private fun createPostDataWithComment(comment: String): PostData {
    return PostData(
      postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 1122, 1123, null),
      postSubjectUnparsed = "",
      postCommentUnparsed = comment,
      images = null,
      threadRepliesTotal = 0,
      threadImagesTotal = 0,
      threadPostersTotal = 0,
      parsedPostData = null
    )
  }

}