package cn.edu.ubaa.api

import cn.edu.ubaa.api.storage.nearbyWeekSerials
import kotlin.test.Test
import kotlin.test.assertEquals

class NearbyWeekSerialsTest {
  @Test
  fun `middle week returns current plus minus radius`() {
    assertEquals(
        listOf(9, 10, 11, 12, 13, 14, 15),
        nearbyWeekSerials(currentSerial = 12, weekCount = 20),
    )
  }

  @Test
  fun `early week clamps at week one`() {
    assertEquals(listOf(1, 2, 3, 4, 5), nearbyWeekSerials(currentSerial = 2, weekCount = 20))
    assertEquals(listOf(1, 2, 3, 4), nearbyWeekSerials(currentSerial = 1, weekCount = 20))
  }

  @Test
  fun `late week clamps at week count`() {
    assertEquals(listOf(17, 18, 19, 20), nearbyWeekSerials(currentSerial = 20, weekCount = 20))
    assertEquals(listOf(16, 17, 18, 19, 20), nearbyWeekSerials(currentSerial = 19, weekCount = 20))
  }

  @Test
  fun `short semester covers all weeks`() {
    assertEquals(listOf(1, 2, 3, 4), nearbyWeekSerials(currentSerial = 2, weekCount = 4))
    assertEquals(listOf(1, 2, 3), nearbyWeekSerials(currentSerial = 1, weekCount = 3))
  }

  @Test
  fun `single week semester`() {
    assertEquals(listOf(1), nearbyWeekSerials(currentSerial = 1, weekCount = 1))
  }

  @Test
  fun `custom radius`() {
    assertEquals(
        listOf(8, 9, 10, 11, 12),
        nearbyWeekSerials(currentSerial = 10, weekCount = 20, radius = 2),
    )
  }
}
