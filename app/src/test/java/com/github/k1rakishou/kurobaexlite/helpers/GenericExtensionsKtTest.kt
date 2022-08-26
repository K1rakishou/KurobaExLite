package com.github.k1rakishou.kurobaexlite.helpers

import com.github.k1rakishou.kurobaexlite.helpers.util.domain
import com.github.k1rakishou.kurobaexlite.helpers.util.quantize
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericExtensionsKtTest {
  private val EPSILON = 0.001

  @Test
  fun quantizePositive() {
    kotlin.run {
      val res = 0.0f.quantize(0.025f)
      assertTrue("res=$res", (0.0f - res).absoluteValue <= EPSILON)
    }

    kotlin.run {
      val res = 1.0f.quantize(0.025f)
      assertTrue("res=$res", (1.0f - res).absoluteValue <= EPSILON)
    }

    kotlin.run {
      val res = 0.025f.quantize(0.025f)
      assertTrue("res=$res", (0.025f - res).absoluteValue <= EPSILON)
    }

    kotlin.run {
      val res = 0.333f.quantize(0.025f)
      assertTrue("res=$res", (0.325f - res).absoluteValue <= EPSILON)
    }

    kotlin.run {
      val res = 0.351f.quantize(0.025f)
      assertTrue("res=$res", (0.35f - res).absoluteValue <= EPSILON)
    }

    kotlin.run {
      val res = 1000f.quantize(25f)
      assertTrue("res=$res", (1000f - res).absoluteValue <= EPSILON)
    }

    kotlin.run {
      val res = 999f.quantize(25f)
      assertEquals(975, res.roundToInt())
    }

    kotlin.run {
      val res = 1000f.quantize(25f)
      assertEquals(1000, res.roundToInt())
    }
  }

  @Test
  fun quantizeNegative() {
    kotlin.run {
      val res = (-0.0f).quantize(0.025f)
      assertTrue("res=$res", ((-0.0f) - res).absoluteValue <= EPSILON)
    }

    kotlin.run {
      val res = (-1.0f).quantize(0.025f)
      assertTrue("res=$res", ((-1.0f) - res).absoluteValue <= EPSILON)
    }

    kotlin.run {
      val res = (-0.025f).quantize(0.025f)
      assertTrue("res=$res", ((-0.025f) - res).absoluteValue <= EPSILON)
    }

    kotlin.run {
      val res = (-0.333f).quantize(0.025f)
      assertTrue("res=$res", ((-0.325f) - res).absoluteValue <= EPSILON)
    }

    kotlin.run {
      val res = (-0.351f).quantize(0.025f)
      assertTrue("res=$res", ((-0.35f) - res).absoluteValue <= EPSILON)
    }

    kotlin.run {
      val res = (-999f).quantize(25f)
      assertEquals(-975, res.roundToInt())
    }

    kotlin.run {
      val res = (-1000f).quantize(25f)
      assertEquals((-1000), res.roundToInt())
    }
  }

  @Test
  fun topDomainTest() {
    assertEquals("4cdn.org", "https://a.4cdn.org".toHttpUrl().domain())
    assertEquals("4cdn.org", "https://sys.4cdn.org".toHttpUrl().domain())
    assertEquals("4cdn.org", "https://q.w.e.r.t.y.4cdn.org".toHttpUrl().domain())
    assertEquals("4chan.org", "https://4chan.org".toHttpUrl().domain())
    assertEquals("wikipedia.ru", "https://wikipedia.ru/%D0%A7%D1%91%D1%80%D0%BD%D1%8B%D0%B9_%D0%B0%D0%B2%D0%B3%D1%83%D1%81%D1%82".toHttpUrl().domain())
  }

}