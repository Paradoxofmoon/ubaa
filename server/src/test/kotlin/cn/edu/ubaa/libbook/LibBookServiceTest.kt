package cn.edu.ubaa.libbook

import cn.edu.ubaa.model.dto.LibBookReserveRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class LibBookServiceTest {
  @Test
  fun `service maps library areas seats bookings and reservation workflow`() = runTest {
    val gateway = FakeLibBookGateway()
    val service = LibBookService(clientProvider = { gateway })

    val libraries = service.getLibraries("2418", "2026-05-08")
    val areas = service.getAreas("2418", "9", "10", "2026-05-08")
    val detail = service.getAreaDetail("2418", "8")
    val seats = service.getSeats("2418", "8", "2026-05-08", "08:00", "23:00")
    val reserve =
        service.reserve(
            "2418",
            LibBookReserveRequest(
                areaId = "8",
                seatId = "101",
                day = "2026-05-08",
                segment = "seg-1",
            ),
        )
    val bookings = service.getBookings("2418", 1, 20)
    val cancel = service.cancelBooking("2418", "b1")

    assertEquals("学院路校区图书馆", libraries.single().name)
    assertEquals("一层", libraries.single().storeys.single().name)
    assertEquals("一层西阅学空间", areas.single().name)
    assertEquals("seg-1", detail.timeSlots.single().id)
    assertEquals(listOf(true, false), seats.map { it.isAvailable })
    assertEquals("操作成功", reserve.message)
    assertEquals(1, bookings.bookings.size)
    assertTrue(cancel.success)
    assertEquals(1, gateway.reserveCalls)
    assertEquals(1, gateway.cancelCalls)
  }

  @Test
  fun `cancel ended booking maps to not found without libbook error retry`() = runTest {
    val gateway =
        object : FakeLibBookGateway() {
          override suspend fun cancelBooking(bookingId: String): JsonObject {
            cancelCalls++
            return buildJsonObject {
              put("code", 2)
              put("message", "预约已结束，不能取消")
            }
          }
        }
    val service = LibBookService(clientProvider = { gateway })

    val error = assertFailsWith<LibBookException> { service.cancelBooking("2418", "b-ended") }

    assertEquals("libbook_not_found", error.code)
    assertEquals(1, gateway.cancelCalls)
  }

  @Test
  fun `reserve libbook error is not retried because reservation is not idempotent`() = runTest {
    val gateway =
        object : FakeLibBookGateway() {
          override suspend fun reserve(request: LibBookReserveRequest): JsonObject {
            reserveCalls++
            return buildJsonObject {
              put("code", 2)
              put("message", "系统繁忙")
            }
          }
        }
    val service = LibBookService(clientProvider = { gateway })

    val error =
        assertFailsWith<LibBookException> {
          service.reserve(
              "2418",
              LibBookReserveRequest(
                  areaId = "8",
                  seatId = "101",
                  day = "2026-05-08",
                  segment = "seg-1",
              ),
          )
        }

    assertEquals("libbook_error", error.code)
    assertEquals(1, gateway.reserveCalls)
  }
}

private open class FakeLibBookGateway : LibBookGateway {
  var reserveCalls = 0
  var cancelCalls = 0

  override suspend fun getLibraries(day: String): JsonArray = buildJsonArray {
    add(
        buildJsonObject {
          put("id", "9")
          put("name", "学院路校区图书馆")
          put("free_num", 12)
          put("total_num", 100)
          putJsonArray("children") {
            add(
                buildJsonObject {
                  put("id", "10")
                  put("name", "一层")
                  put("free_num", 5)
                  put("total_num", 30)
                }
            )
          }
        }
    )
  }

  override suspend fun getAreas(premisesId: String, storeyId: String?, day: String): JsonArray =
      buildJsonArray {
        add(
            buildJsonObject {
              put("id", "8")
              put("name", "一层西阅学空间")
              put("area", "学院路")
              put("free_num", 2)
              put("total_num", 10)
            }
        )
      }

  override suspend fun getAreaInfo(areaId: String): JsonObject = buildJsonObject {
    put("code", 1)
    putJsonObject("data") {
      putJsonObject("area") {
        put("id", "8")
        put("name", "一层西阅学空间")
      }
      putJsonObject("date") {
        putJsonArray("list") {
          add(
              buildJsonObject {
                put("day", "2026-05-08")
                putJsonArray("times") {
                  add(
                      buildJsonObject {
                        put("id", "seg-1")
                        put("start", "08:00")
                        put("end", "23:00")
                      }
                  )
                }
              }
          )
        }
      }
    }
  }

  override suspend fun getSeats(
      areaId: String,
      day: String,
      startTime: String,
      endTime: String,
  ): JsonArray = buildJsonArray {
    add(
        buildJsonObject {
          put("id", "101")
          put("no", "101")
          put("status", "1")
          put("status_name", "空闲")
        }
    )
    add(
        buildJsonObject {
          put("id", "102")
          put("no", "102")
          put("status", "2")
          put("status_name", "已占用")
        }
    )
  }

  override suspend fun reserve(request: LibBookReserveRequest): JsonObject {
    reserveCalls++
    return buildJsonObject {
      put("code", 1)
      put("message", "操作成功")
      putJsonObject("data") {
        putJsonObject("bookInfo") {
          put("id", "b1")
          put("nameMerge", "一层 / 101")
          put("no", "101")
        }
      }
    }
  }

  override suspend fun getBookings(page: Int, limit: Int): JsonObject = buildJsonObject {
    put("code", 1)
    putJsonObject("data") {
      put("total", 1)
      putJsonArray("data") {
        add(
            buildJsonObject {
              put("id", "b1")
              put("nameMerge", "一层 / 101")
              put("no", "101")
              put("status_name", "已预约")
            }
        )
      }
    }
  }

  override suspend fun cancelBooking(bookingId: String): JsonObject {
    cancelCalls++
    return buildJsonObject {
      put("code", 1)
      put("message", "取消成功")
    }
  }

  override fun close() = Unit
}
