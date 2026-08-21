package cn.edu.ubaa.libbook

import cn.edu.ubaa.model.dto.LibBookAreaDetailDto
import cn.edu.ubaa.model.dto.LibBookAreaDto
import cn.edu.ubaa.model.dto.LibBookBookingDto
import cn.edu.ubaa.model.dto.LibBookBookingsResponse
import cn.edu.ubaa.model.dto.LibBookCancelResponse
import cn.edu.ubaa.model.dto.LibBookLibraryDto
import cn.edu.ubaa.model.dto.LibBookReserveRequest
import cn.edu.ubaa.model.dto.LibBookReserveResponse
import cn.edu.ubaa.model.dto.LibBookSeatDto
import cn.edu.ubaa.model.dto.LibBookStoreyDto
import cn.edu.ubaa.model.dto.LibBookTimeSlotDto
import cn.edu.ubaa.utils.withUpstreamDeadline
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LibBookService(
    private val clientProvider: (String) -> LibBookGateway = ::LibBookClient,
) {
  private data class CachedClient(
      val client: LibBookGateway,
      @Volatile var lastAccessAt: Long,
  )

  private val clientCache = ConcurrentHashMap<String, CachedClient>()

  suspend fun getLibraries(username: String, day: String): List<LibBookLibraryDto> =
      withLibBookDeadline("图书馆楼馆列表加载超时") {
        withFreshClientRetry(username, "list_libraries") { client ->
          client.getLibraries(day).map(::mapLibrary)
        }
      }

  suspend fun getAreas(
      username: String,
      premisesId: String,
      storeyId: String?,
      day: String,
  ): List<LibBookAreaDto> =
      withLibBookDeadline("图书馆分区列表加载超时") {
        if (premisesId.isBlank()) throw LibBookException("缺少楼馆参数", "invalid_request")
        withFreshClientRetry(username, "list_areas") { client ->
          client.getAreas(premisesId, storeyId, day).map(::mapArea)
        }
      }

  suspend fun getAreaDetail(username: String, areaId: String): LibBookAreaDetailDto =
      withLibBookDeadline("图书馆分区信息加载超时") {
        if (areaId.isBlank()) throw LibBookException("缺少分区参数", "invalid_request")
        withFreshClientRetry(username, "get_area_info") { client ->
          mapAreaDetail(areaId, client.getAreaInfo(areaId))
        }
      }

  suspend fun getSeats(
      username: String,
      areaId: String,
      day: String,
      startTime: String,
      endTime: String,
  ): List<LibBookSeatDto> =
      withLibBookDeadline("图书馆座位列表加载超时") {
        if (areaId.isBlank() || day.isBlank()) throw LibBookException("缺少座位查询参数", "invalid_request")
        withFreshClientRetry(username, "list_seats") { client ->
          client.getSeats(areaId, day, startTime, endTime).map(::mapSeat).sortedBy { it.no }
        }
      }

  suspend fun reserve(username: String, request: LibBookReserveRequest): LibBookReserveResponse =
      withLibBookDeadline("图书馆座位预约超时", 10.seconds) {
        validateReserveRequest(request)
        withFreshClientRetry(username, "reserve", retryGenericErrors = false) { client ->
          val response = client.reserve(request)
          val message = response.messageOrDefault("预约成功")
          if (!response.isBusinessSuccess()) {
            throw LibBookException(message, response.messageToErrorCode())
          }
          LibBookReserveResponse(
              success = true,
              message = message,
              booking =
                  response["data"]?.jsonObjectOrNull()?.let { data ->
                    data["bookInfo"]?.jsonObjectOrNull()?.let(::mapBooking)
                        ?: data["booking"]?.jsonObjectOrNull()?.let(::mapBooking)
                  },
          )
        }
      }

  suspend fun getBookings(username: String, page: Int, limit: Int): LibBookBookingsResponse =
      withLibBookDeadline("图书馆预约列表加载超时") {
        withFreshClientRetry(username, "list_bookings") { client ->
          val raw = client.getBookings(page, limit)
          val data = raw["data"]?.jsonObjectOrNull()
          val bookings =
              data?.get("data")?.jsonArrayOrNull()?.map(::mapBooking)
                  ?: data?.get("list")?.jsonArrayOrNull()?.map(::mapBooking)
                  ?: emptyList()
          LibBookBookingsResponse(
              bookings = bookings,
              page = data?.int("current_page") ?: data?.int("page") ?: page,
              limit = data?.int("per_page") ?: data?.int("limit") ?: limit,
              total = data?.int("total") ?: bookings.size,
          )
        }
      }

  suspend fun cancelBooking(username: String, bookingId: String): LibBookCancelResponse =
      withLibBookDeadline("图书馆预约取消超时") {
        if (bookingId.isBlank()) throw LibBookException("预约记录不存在或已失效", "libbook_not_found")
        withFreshClientRetry(username, "cancel_booking", retryGenericErrors = false) { client ->
          val response = client.cancelBooking(bookingId)
          val message = response.messageOrDefault("取消成功")
          if (!response.isBusinessSuccess()) {
            throw LibBookException(message, response.messageToErrorCode())
          }
          LibBookCancelResponse(success = true, message = message)
        }
      }

  fun cleanupExpiredClients(maxIdleMillis: Long = DEFAULT_MAX_IDLE_MILLIS): Int {
    val cutoff = System.currentTimeMillis() - maxIdleMillis
    var removed = 0
    for ((username, cached) in clientCache.entries.toList()) {
      if (cached.lastAccessAt >= cutoff) continue
      if (!clientCache.remove(username, cached)) continue
      cached.client.close()
      removed++
    }
    return removed
  }

  fun cacheSize(): Int = clientCache.size

  fun clearCache() {
    clientCache.values.forEach { it.client.close() }
    clientCache.clear()
  }

  private fun getClient(username: String): LibBookGateway {
    val now = System.currentTimeMillis()
    return clientCache
        .compute(username) { _, existing ->
          existing?.also { it.lastAccessAt = now } ?: CachedClient(clientProvider(username), now)
        }!!
        .client
  }

  private suspend fun <T> withFreshClientRetry(
      username: String,
      operation: String,
      retryGenericErrors: Boolean = true,
      block: suspend (LibBookGateway) -> T,
  ): T =
      try {
        block(getClient(username))
      } catch (e: LibBookAuthenticationException) {
        discardClient(username)
        block(getClient(username))
      } catch (e: LibBookException) {
        if (!retryGenericErrors || e.code != "libbook_error") throw e
        discardClient(username)
        block(getClient(username))
      }

  private fun discardClient(username: String) {
    clientCache.remove(username)?.client?.close()
  }

  private fun validateReserveRequest(request: LibBookReserveRequest) {
    if (request.areaId.isBlank()) throw LibBookException("缺少分区参数", "invalid_request")
    if (request.seatId.isBlank()) throw LibBookException("请选择座位", "invalid_request")
    if (request.day.isBlank() || request.segment.isBlank()) {
      throw LibBookException("预约时间段无效，请刷新后重试", "invalid_request")
    }
  }

  private suspend fun <T> withLibBookDeadline(
      message: String,
      timeout: kotlin.time.Duration = 9.seconds,
      block: suspend () -> T,
  ): T = withUpstreamDeadline(timeout, message, "libbook_timeout", block)

  companion object {
    private const val DEFAULT_MAX_IDLE_MILLIS = 30 * 60 * 1000L
  }
}

object GlobalLibBookService {
  val instance: LibBookService by lazy { LibBookService() }
}

private fun mapLibrary(element: JsonElement): LibBookLibraryDto {
  val raw = element.jsonObject
  return LibBookLibraryDto(
      id = raw.string("id"),
      name = raw.string("name"),
      freeNum = raw.int("free_num"),
      totalNum = raw.int("total_num"),
      storeys = raw["children"]?.jsonArrayOrNull()?.map(::mapStorey).orEmpty(),
  )
}

private fun mapStorey(element: JsonElement): LibBookStoreyDto {
  val raw = element.jsonObject
  return LibBookStoreyDto(
      id = raw.string("id"),
      name = raw.string("name"),
      freeNum = raw.int("free_num"),
      totalNum = raw.int("total_num"),
  )
}

private fun mapArea(element: JsonElement): LibBookAreaDto {
  val raw = element.jsonObject
  return LibBookAreaDto(
      id = raw.string("id"),
      name = raw.string("name"),
      areaName = raw.string("area"),
      premisesId = raw.string("premises_id").ifBlank { raw.string("premisesId") },
      storeyId = raw.string("storey_id").ifBlank { raw.string("storeyId") },
      freeNum = raw.int("free_num"),
      totalNum = raw.int("total_num"),
  )
}

private fun mapAreaDetail(areaId: String, raw: JsonObject): LibBookAreaDetailDto {
  val data = raw["data"]?.jsonObjectOrNull()
  val area = data?.get("area")?.jsonObjectOrNull()
  val dateList = data?.get("date")?.jsonObjectOrNull()?.get("list")?.jsonArrayOrNull().orEmpty()
  val availableDates =
      dateList.mapNotNull { date ->
        val item = date.jsonObject
        item.string("day").ifBlank { item.string("date") }.takeIf { it.isNotBlank() }
      }
  val timeSlots =
      dateList
          .firstOrNull()
          ?.jsonObject
          ?.get("times")
          ?.jsonArrayOrNull()
          ?.mapNotNull { element ->
            val item = element.jsonObject
            val id = item.string("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LibBookTimeSlotDto(id = id, start = item.string("start"), end = item.string("end"))
          }
          .orEmpty()
  return LibBookAreaDetailDto(
      id = area?.string("id").orEmpty().ifBlank { areaId },
      name = area?.string("name").orEmpty(),
      availableDates = availableDates,
      timeSlots = timeSlots,
  )
}

private fun mapSeat(element: JsonElement): LibBookSeatDto {
  val raw = element.jsonObject
  val status = raw.string("status")
  return LibBookSeatDto(
      id = raw.string("id"),
      name = raw.string("name"),
      no = raw.string("no"),
      status = status,
      statusName = raw.string("status_name"),
      isAvailable = status == "1",
  )
}

private fun mapBooking(element: JsonElement): LibBookBookingDto {
  val raw = element.jsonObject
  return LibBookBookingDto(
      id = raw.string("id"),
      nameMerge = raw.string("nameMerge").ifBlank { raw.string("name_merge") },
      areaName = raw.string("name").ifBlank { raw.string("area_name") },
      seatNo = raw.string("no").ifBlank { raw.string("seat_no") },
      day = raw.string("day").ifBlank { raw.string("date") },
      beginTime = raw.string("beginTime").ifBlank { raw.string("begin_time") },
      endTime = raw.string("endTime").ifBlank { raw.string("end_time") },
      status = raw.string("status"),
      statusName = raw.string("status_name"),
  )
}

private fun JsonObject.isBusinessSuccess(): Boolean {
  val code = this["code"]?.jsonPrimitive?.intOrNull
  if (code != null && code !in setOf(0, 1)) return false
  val message = messageOrDefault("")
  return !message.contains("失败") &&
      !message.contains("不可") &&
      !message.contains("已被") &&
      !message.contains("不能取消") &&
      !message.contains("无法取消") &&
      !message.contains("已取消") &&
      !message.contains("用户取消") &&
      !message.contains("已结束") &&
      !message.contains("已完成")
}
