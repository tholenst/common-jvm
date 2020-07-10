package org.wfanet.measurement.common

import com.google.common.truth.Truth.assertThat
import io.grpc.ConnectivityState
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

@RunWith(JUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChannelShutdownHookTest {
  @get:Rule
  val grpcCleanup = GrpcCleanupRule()

  @Test
  fun `shutdown some channels`() = runBlocking {
    val channels = List(5) {
      val serverName = InProcessServerBuilder.generateName()
      val channel =
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
      grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
          .directExecutor()
          .addService(FakeServiceImpl(serverName))
          .build()
          .start()
      )
      channel
    }

    // Launch some endless streams but leave a couple of servers idle.
    // Idle channels shut down immediately.
    // Channels with on-going RPCs wait for the timeout before shutting down.
    val streamJobs = channels.take(3).flatMap {
      val client = FakeServiceGrpcKt.FakeServiceCoroutineStub(it)
      // Create multiple streams for each server for good measure.
      val jobs = (1..3).map { n ->
        GlobalScope.launch {
          val requests = flow {
            while (true) {
              emit(
                FakeRequest.newBuilder().setNumber(n).build()
              )
              delay(200)
            }
          }
          client.fake(requests)
        }
      }
      // Wait for the streams to connect before attempting to shut the channels down.
      while (it.getState(false) != ConnectivityState.READY) {
        delay(10)
      }
      jobs
    }

    // Mock the Runtime in order to capture the shutdown hooks as they are added.
    val shutdownHookCaptor = ArgumentCaptor.forClass(Thread::class.java)
    val runtime = Mockito.mock(Runtime::class.java)
    Mockito.doNothing().`when`(runtime).addShutdownHook(shutdownHookCaptor.capture())

    // Add channel shutdown hooks to the mock Runtime.
    addChannelShutdownHooks(runtime, Duration.ofMillis(500), *channels.toTypedArray())

    // Run captured shutdown hooks in parallel to simulate JVM shutdown.
    val hookJobs = shutdownHookCaptor.allValues.map {
      launch(Dispatchers.Default) {
        it.run()
      }
    }

    // Wait for the hooks to complete.
    hookJobs.forEach { it.join() }
    // Kill the infinite streams.
    streamJobs.forEach { it.cancelAndJoin() }

    channels.forEach {
      assertThat(it.isTerminated).isTrue()
    }
  }
}

private class FakeServiceImpl(private val serverName: String) :
  FakeServiceGrpcKt.FakeServiceCoroutineImplBase() {
  override suspend fun fake(requests: Flow<FakeRequest>): FakeResponse {
    requests.collect {
      println("*** Server $serverName received: $it")
    }
    return FakeResponse.getDefaultInstance()
  }
}
