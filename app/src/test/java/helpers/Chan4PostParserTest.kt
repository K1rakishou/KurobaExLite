package helpers

import com.github.k1rakishou.kurobaexlite.helpers.parser.Chan4PostParser
import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPartSpan
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import org.junit.Assert
import org.junit.Test

class Chan4PostParserTest {

  @Test
  fun internalQuote() {
    val postCommentParser = Chan4PostParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "#p370525473",
      postDescriptor = postDescriptor
    )

    linkable as TextPartSpan.Linkable.Quote
    Assert.assertEquals("test", linkable.postDescriptor.boardCode)
    Assert.assertEquals(111, linkable.postDescriptor.threadNo)
    Assert.assertEquals(370525473, linkable.postDescriptor.postNo)
  }

  @Test
  fun catalogSearchLink1() {
    val postCommentParser = Chan4PostParser()

    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4channel.org/vg/catalog#s=tesog%2F",
      postDescriptor = postDescriptor
    )

    linkable as TextPartSpan.Linkable.Search
    Assert.assertEquals("vg", linkable.boardCode)
    Assert.assertEquals("tesog", linkable.searchQuery)
  }

  @Test
  fun catalogSearchLink2() {
    val postCommentParser = Chan4PostParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4chan.org/g/catalog#s=sqt%2F",
      postDescriptor = postDescriptor
    )
    linkable as TextPartSpan.Linkable.Search
    Assert.assertEquals("g", linkable.boardCode)
    Assert.assertEquals("sqt", linkable.searchQuery)
  }

  @Test
  fun crossThreadLink1() {
    val postCommentParser = Chan4PostParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4channel.org/qst/thread/5126311#p5126312",
      postDescriptor = postDescriptor
    )
    linkable as TextPartSpan.Linkable.Quote
    Assert.assertEquals("qst", linkable.postDescriptor.boardCode)
    Assert.assertEquals(5126311, linkable.postDescriptor.threadNo)
    Assert.assertEquals(5126312, linkable.postDescriptor.postNo)
  }

  @Test
  fun crossThreadLink2() {
    val postCommentParser = Chan4PostParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4chan.org/aco/thread/6147349#p6147349",
      postDescriptor = postDescriptor
    )
    linkable as TextPartSpan.Linkable.Quote
    Assert.assertEquals("aco", linkable.postDescriptor.boardCode)
    Assert.assertEquals(6147349, linkable.postDescriptor.threadNo)
    Assert.assertEquals(6147349, linkable.postDescriptor.postNo)
  }

  @Test
  fun crossThreadLink3() {
    val postCommentParser = Chan4PostParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4channel.org/aco/thread/6149612",
      postDescriptor = postDescriptor
    )
    linkable as TextPartSpan.Linkable.Quote
    Assert.assertEquals("aco", linkable.postDescriptor.boardCode)
    Assert.assertEquals(6149612, linkable.postDescriptor.threadNo)
    Assert.assertEquals(6149612, linkable.postDescriptor.postNo)
  }

  @Test
  fun crossThreadLink4() {
    val postCommentParser = Chan4PostParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "/vg/thread/369649921#p369650787",
      postDescriptor = postDescriptor
    )
    linkable as TextPartSpan.Linkable.Quote
    Assert.assertEquals("vg", linkable.postDescriptor.boardCode)
    Assert.assertEquals(369649921, linkable.postDescriptor.threadNo)
    Assert.assertEquals(369650787, linkable.postDescriptor.postNo)
  }

  @Test
  fun crossThreadLink5() {
    val postCommentParser = Chan4PostParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "/vg/thread/369649921",
      postDescriptor = postDescriptor
    )
    linkable as TextPartSpan.Linkable.Quote
    Assert.assertEquals("vg", linkable.postDescriptor.boardCode)
    Assert.assertEquals(369649921, linkable.postDescriptor.threadNo)
    Assert.assertEquals(369649921, linkable.postDescriptor.postNo)
  }

  @Test
  fun boardLink1() {
    val postCommentParser = Chan4PostParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4channel.org/jp/",
      postDescriptor = postDescriptor
    )
    linkable as TextPartSpan.Linkable.Board
    Assert.assertEquals("jp", linkable.boardCode)
  }

  @Test
  fun boardLink2() {
    val postCommentParser = Chan4PostParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4chan.org/g/",
      postDescriptor = postDescriptor
    )
    linkable as TextPartSpan.Linkable.Board
    Assert.assertEquals("g", linkable.boardCode)
  }

}