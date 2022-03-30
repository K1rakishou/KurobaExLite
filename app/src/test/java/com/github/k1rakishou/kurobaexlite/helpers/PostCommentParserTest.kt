package com.github.k1rakishou.kurobaexlite.helpers

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.SiteKey
import org.junit.Assert.assertEquals
import org.junit.Test

class PostCommentParserTest {

  @Test
  fun internalQuote() {
    val postCommentParser = PostCommentParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "#p370525473",
      postDescriptor = postDescriptor
    )

    linkable as PostCommentParser.TextPartSpan.Linkable.Quote
    assertEquals("test", linkable.postDescriptor.boardCode)
    assertEquals(111, linkable.postDescriptor.threadNo)
    assertEquals(370525473, linkable.postDescriptor.postNo)
  }

  @Test
  fun catalogSearchLink1() {
    val postCommentParser = PostCommentParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4channel.org/vg/catalog#s=tesog%2F",
      postDescriptor = postDescriptor
    )

    linkable as PostCommentParser.TextPartSpan.Linkable.Search
    assertEquals("vg", linkable.boardCode)
    assertEquals("tesog", linkable.searchQuery)
  }

  @Test
  fun catalogSearchLink2() {
    val postCommentParser = PostCommentParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4chan.org/g/catalog#s=sqt%2F",
      postDescriptor = postDescriptor
    )
    linkable as PostCommentParser.TextPartSpan.Linkable.Search
    assertEquals("g", linkable.boardCode)
    assertEquals("sqt", linkable.searchQuery)
  }

  @Test
  fun crossThreadLink1() {
    val postCommentParser = PostCommentParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4channel.org/qst/thread/5126311#p5126312",
      postDescriptor = postDescriptor
    )
    linkable as PostCommentParser.TextPartSpan.Linkable.Quote
    assertEquals("qst", linkable.postDescriptor.boardCode)
    assertEquals(5126311, linkable.postDescriptor.threadNo)
    assertEquals(5126312, linkable.postDescriptor.postNo)
  }

  @Test
  fun crossThreadLink2() {
    val postCommentParser = PostCommentParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4chan.org/aco/thread/6147349#p6147349",
      postDescriptor = postDescriptor
    )
    linkable as PostCommentParser.TextPartSpan.Linkable.Quote
    assertEquals("aco", linkable.postDescriptor.boardCode)
    assertEquals(6147349, linkable.postDescriptor.threadNo)
    assertEquals(6147349, linkable.postDescriptor.postNo)
  }

  @Test
  fun crossThreadLink3() {
    val postCommentParser = PostCommentParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4channel.org/aco/thread/6149612",
      postDescriptor = postDescriptor
    )
    linkable as PostCommentParser.TextPartSpan.Linkable.Quote
    assertEquals("aco", linkable.postDescriptor.boardCode)
    assertEquals(6149612, linkable.postDescriptor.threadNo)
    assertEquals(6149612, linkable.postDescriptor.postNo)
  }

  @Test
  fun crossThreadLink4() {
    val postCommentParser = PostCommentParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "/vg/thread/369649921#p369650787",
      postDescriptor = postDescriptor
    )
    linkable as PostCommentParser.TextPartSpan.Linkable.Quote
    assertEquals("vg", linkable.postDescriptor.boardCode)
    assertEquals(369649921, linkable.postDescriptor.threadNo)
    assertEquals(369650787, linkable.postDescriptor.postNo)
  }

  @Test
  fun crossThreadLink5() {
    val postCommentParser = PostCommentParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "/vg/thread/369649921",
      postDescriptor = postDescriptor
    )
    linkable as PostCommentParser.TextPartSpan.Linkable.Quote
    assertEquals("vg", linkable.postDescriptor.boardCode)
    assertEquals(369649921, linkable.postDescriptor.threadNo)
    assertEquals(369649921, linkable.postDescriptor.postNo)
  }

  @Test
  fun boardLink1() {
    val postCommentParser = PostCommentParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4channel.org/jp/",
      postDescriptor = postDescriptor
    )
    linkable as PostCommentParser.TextPartSpan.Linkable.Board
    assertEquals("jp", linkable.boardCode)
  }

  @Test
  fun boardLink2() {
    val postCommentParser = PostCommentParser()
    val postDescriptor = PostDescriptor.create(SiteKey("test"), "test", 111, 111)

    val linkable = postCommentParser.parseLinkable(
      className = "quotelink",
      href = "//boards.4chan.org/g/",
      postDescriptor = postDescriptor
    )
    linkable as PostCommentParser.TextPartSpan.Linkable.Board
    assertEquals("g", linkable.boardCode)
  }

}