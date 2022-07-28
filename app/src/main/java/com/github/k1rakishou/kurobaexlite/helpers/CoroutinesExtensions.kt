package com.github.k1rakishou.kurobaexlite.helpers

import java.util.concurrent.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

fun <T> Flow<T>.buffer(delay: Duration, emitIfEmpty: Boolean = true): Flow<List<T>> {
  return flow {
    coroutineScope {
      val collection = mutableListOf<T>()
      val values = produce<T> { collect { value -> send(value) } }

      var done = false
      val ticker = fixedPeriodTicker(delay)

      while (isActive && !done) {
        select<Unit> {
          values.onReceiveCatching { result ->
            result
              .onSuccess { value ->
                collection += value
              }
              .onFailure { error ->
                error?.let { throw error }
                ticker.cancel(CancellationException())
                done = true
              }
          }

          ticker.onReceive {
            val collectionCopy = collection.toList()
            collection.clear()

            if (collectionCopy.isNotEmpty() || emitIfEmpty) {
              emit(collectionCopy)
            }
          }
        }
      }
    }
  }
}

private fun CoroutineScope.fixedPeriodTicker(
  delay: Duration,
  initialDelay: Duration = delay
): ReceiveChannel<Unit> {
  return produce(capacity = 0) {
    delay(initialDelay)

    while (isActive) {
      channel.send(Unit)
      delay(delay)
    }
  }
}