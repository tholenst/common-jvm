package org.wfanet.measurement.common

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // for runBlockingTest
class MinimumIntervalThrottlerTest {
  @Test
  fun onReady() = runBlocking<Unit> {
    val throttler = MinimumIntervalThrottler(Clock.systemUTC(), Duration.ofSeconds(3))
    assertTrue(throttler.onReady { true }) // Reset the last event time to now.

    val latch = CountDownLatch(1)

    withTimeout(Duration.ofSeconds(4).toMillis()) {
      runBlockingTest {
        throttler.onReady { latch.countDown() }
      }
    }

    assertEquals(latch.count, 0)
  }

  @Test
  fun fifo() = runBlockingTest {
    val throttler = MinimumIntervalThrottler(Clock.systemUTC(), Duration.ofMillis(1))

    val order = mutableListOf<String>()

    val m = Mutex(locked = true)

    // This should run last.
    val job1 = launch { println(1); delay(200); println(2); throttler.onReady { order.add("job1") } }

    // This should run second.
    val job2 = launch { println(3); delay(100); println(4); throttler.onReady { order.add("job2") } }

    // This should hit throttler.onReady first, but then get stuck acquiring m.
    val job3 = launch { println(5); throttler.onReady { println(6); m.withLock { order.add("job3") } } }

    // After waiting 1s, job1 and job2 should be blocked on job3 finishing, which is blocked on
    // acquiring m.
    delay(1000)

    m.unlock()
    job1.join()
    job2.join()
    job3.join()

    assertThat(order)
      .containsExactly("job3", "job2", "job1")
      .inOrder()
  }
}
