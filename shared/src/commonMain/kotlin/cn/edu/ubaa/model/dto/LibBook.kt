package cn.edu.ubaa.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LibBookLibraryDto(
    val id: String,
    val name: String,
    val freeNum: Int = 0,
    val totalNum: Int = 0,
    val storeys: List<LibBookStoreyDto> = emptyList(),
)

@Serializable
data class LibBookStoreyDto(
    val id: String,
    val name: String,
    val freeNum: Int = 0,
    val totalNum: Int = 0,
)

@Serializable
data class LibBookAreaDto(
    val id: String,
    val name: String,
    val areaName: String = "",
    val premisesId: String = "",
    val storeyId: String = "",
    val freeNum: Int = 0,
    val totalNum: Int = 0,
)

@Serializable
data class LibBookTimeSlotDto(
    val id: String,
    val start: String,
    val end: String,
    val label: String = "$start-$end",
)

@Serializable
data class LibBookAreaDetailDto(
    val id: String,
    val name: String = "",
    val availableDates: List<String> = emptyList(),
    val timeSlots: List<LibBookTimeSlotDto> = emptyList(),
)

@Serializable
data class LibBookSeatDto(
    val id: String,
    val name: String = "",
    val no: String = "",
    val status: String = "",
    val statusName: String = "",
    val isAvailable: Boolean = status == "1",
)

@Serializable
data class LibBookReserveRequest(
    val areaId: String,
    val seatId: String,
    val day: String,
    val segment: String,
    val startTime: String = "",
    val endTime: String = "",
)

@Serializable
data class LibBookReserveResponse(
    val success: Boolean,
    val message: String,
    val booking: LibBookBookingDto? = null,
)

@Serializable
data class LibBookCancelResponse(
    val success: Boolean,
    val message: String,
)

@Serializable
data class LibBookBookingsResponse(
    val bookings: List<LibBookBookingDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = bookings.size,
)

@Serializable
data class LibBookBookingDto(
    val id: String,
    val nameMerge: String = "",
    val areaName: String = "",
    val seatNo: String = "",
    val day: String = "",
    val beginTime: String = "",
    val endTime: String = "",
    val status: String = "",
    val statusName: String = "",
)

fun LibBookBookingDto.canCancelBooking(): Boolean = cancelBlockedMessage() == null

fun LibBookBookingDto.cancelBlockedMessage(): String? {
  if (id.isBlank()) return "预约记录不存在或已失效，请刷新后重试"
  if (status.trim() in nonCancelableLibBookBookingStatusCodes) {
    return "该预约已结束或已取消，无需取消"
  }
  if (nonCancelableLibBookBookingStatusKeywords.any { statusName.contains(it) }) {
    return "该预约已结束或已取消，无需取消"
  }
  return null
}

private val nonCancelableLibBookBookingStatusCodes = setOf("6", "8")

private val nonCancelableLibBookBookingStatusKeywords = listOf("取消", "结束", "已完成", "过期", "失效")

@Serializable
data class LibBookEncryptedReserveBody(
    @SerialName("seat_id") val seatId: String,
    val segment: String,
    val day: String,
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String = "",
)
