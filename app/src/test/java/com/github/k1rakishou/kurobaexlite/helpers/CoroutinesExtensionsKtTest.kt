package com.github.k1rakishou.kurobaexlite.helpers

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutinesExtensionsKtTest {

  @Test
  fun testCustomBufferOperator() = runTest {
    val flow = flow<Int> {
      repeat(10) { x1 ->
        repeat(10) { x2 ->
          val value = (x1 * 10) + x2
          emit(value)
        }

        delay(100)
      }
    }

    val values = mutableListOf<Int>()
    var prevTime = 0L

    val job = launch {
      flow
        .buffer(100.milliseconds)
        .collect { batch ->
          val timeDelta = currentTime - prevTime
          assertTrue("Bad time delta: ${timeDelta}", timeDelta >= 100)

          assertEquals(10, batch.size)
          values.addAll(batch)

          prevTime = currentTime
        }
    }

    job.join()
    assertEquals(100, values.size)
  }

}