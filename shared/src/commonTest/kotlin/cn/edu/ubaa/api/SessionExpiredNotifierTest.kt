package cn.edu.ubaa.api

import cn.edu.ubaa.api.local.localUnauthenticatedApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SessionExpiredNotifierTest {

  @Test
  fun `unauthenticated exception emits session expired event`() = runTest {
    val received = mutableListOf<Unit>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      SessionExpiredNotifier.events.collect { received += it }
    }

    localUnauthenticatedApiException()
    advanceUntilIdle()

    assertEquals(1, received.size)
  }
}
