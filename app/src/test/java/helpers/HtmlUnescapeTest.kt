package helpers

import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import junit.framework.TestCase
import org.junit.Test

class HtmlUnescapeTest : TestCase() {

  @Test
  fun testUnescapeAsciiCharacter() {
    val resultString = HtmlUnescape.unescape("Test &#039;&#039;&#039; Test")
    assertEquals("Test \'\'\' Test", resultString)
  }

  @Test
  fun testUnescapeGtCharacter() {
    val resultString = HtmlUnescape.unescape("Test &gt;&gt;&gt; Test")
    assertEquals("Test >>> Test", resultString)
  }

  @Test
  fun testUnescapeLtCharacter() {
    val resultString = HtmlUnescape.unescape("Test &lt;&lt;&lt; Test")
    assertEquals("Test <<< Test", resultString)
  }

  @Test
  fun testUnescapeQuotCharacter() {
    val resultString = HtmlUnescape.unescape("Test &quot;Test&quot; Test")
    assertEquals("Test \"Test\" Test", resultString)
  }

  @Test
  fun testBrokenEscapedCharacter_1() {
    val resultString = HtmlUnescape.unescape("Test &#039;&#039&#039; Test")
    assertEquals("Test \'&#039\' Test", resultString)
  }

  @Test
  fun testBrokenEscapedCharacter_2() {
    val resultString = HtmlUnescape.unescape("Test &#039;&#039;&#")
    assertEquals("Test ''&#", resultString)
  }

}