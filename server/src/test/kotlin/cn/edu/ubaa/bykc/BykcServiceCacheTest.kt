package cn.edu.ubaa.bykc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class BykcServiceCacheTest {

  @Test
  fun cleanupExpiredClientsClosesRemovedClientsOnly() {
    val service = BykcService(clientProvider = { username -> TrackingBykcClient(username) })
    val now = System.currentTimeMillis()
    val expiredClient = TrackingBykcClient("expired")
    val activeClient = TrackingBykcClient("active")

    service.cacheClientForTesting("expired", expiredClient, now - 31 * 60 * 1000L)
    service.cacheClientForTesting("active", activeClient, now)

    val removed = service.cleanupExpiredClients()

    assertEquals(1, removed)
    assertEquals(1, expiredClient.closeCount)
    assertEquals(0, activeClient.closeCount)
    assertNull(service.cachedClientForTesting("expired"))
    assertSame(activeClient, service.cachedClientForTesting("active"))
  }

  @Test
  fun clearCacheClosesAllClients() {
    val service = BykcService(clientProvider = { username -> TrackingBykcClient(username) })
    val firstClient = TrackingBykcClient("first")
    val secondClient = TrackingBykcClient("second")

    service.cacheClientForTesting("first", firstClient)
    service.cacheClientForTesting("second", secondClient)

    service.clearCache()

    assertEquals(1, firstClient.closeCount)
    assertEquals(1, secondClient.closeCount)
    assertEquals(0, service.cacheSize())
  }

  private class TrackingBykcClient(username: String) : BykcClient(username) {
    var closeCount = 0
      private set

    override fun close() {
      closeCount++
      super.close()
    }
  }
}
