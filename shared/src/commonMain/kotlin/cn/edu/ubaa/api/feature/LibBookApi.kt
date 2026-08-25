package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.model.dto.LibBookAreaDetailDto
import cn.edu.ubaa.model.dto.LibBookAreaDto
import cn.edu.ubaa.model.dto.LibBookBookingsResponse
import cn.edu.ubaa.model.dto.LibBookCancelResponse
import cn.edu.ubaa.model.dto.LibBookLibraryDto
import cn.edu.ubaa.model.dto.LibBookReserveRequest
import cn.edu.ubaa.model.dto.LibBookReserveResponse
import cn.edu.ubaa.model.dto.LibBookSeatDto

interface LibBookApiBackend {
  suspend fun getLibraries(day: String): Result<List<LibBookLibraryDto>>

  suspend fun getAreas(
      premisesId: String,
      storeyId: String?,
      day: String,
  ): Result<List<LibBookAreaDto>>

  suspend fun getAreaDetail(areaId: String): Result<LibBookAreaDetailDto>

  suspend fun getSeats(
      areaId: String,
      day: String,
      startTime: String,
      endTime: String,
  ): Result<List<LibBookSeatDto>>

  suspend fun reserve(request: LibBookReserveRequest): Result<LibBookReserveResponse>

  suspend fun getBookings(page: Int = 1, limit: Int = 20): Result<LibBookBookingsResponse>

  suspend fun cancelBooking(bookingId: String): Result<LibBookCancelResponse>
}

open class LibBookApi(
    private val backendProvider: () -> LibBookApiBackend = {
      ConnectionRuntime.apiFactory().libBookApi()
    }
) {
  internal constructor(backend: LibBookApiBackend) : this({ backend })

  private fun currentBackend(): LibBookApiBackend = backendProvider()

  open suspend fun getLibraries(day: String): Result<List<LibBookLibraryDto>> =
      currentBackend().getLibraries(day)

  open suspend fun getAreas(
      premisesId: String,
      storeyId: String? = null,
      day: String,
  ): Result<List<LibBookAreaDto>> = currentBackend().getAreas(premisesId, storeyId, day)

  open suspend fun getAreaDetail(areaId: String): Result<LibBookAreaDetailDto> =
      currentBackend().getAreaDetail(areaId)

  open suspend fun getSeats(
      areaId: String,
      day: String,
      startTime: String,
      endTime: String,
  ): Result<List<LibBookSeatDto>> = currentBackend().getSeats(areaId, day, startTime, endTime)

  open suspend fun reserve(request: LibBookReserveRequest): Result<LibBookReserveResponse> =
      currentBackend().reserve(request)

  open suspend fun getBookings(page: Int = 1, limit: Int = 20): Result<LibBookBookingsResponse> =
      currentBackend().getBookings(page, limit)

  open suspend fun cancelBooking(bookingId: String): Result<LibBookCancelResponse> =
      currentBackend().cancelBooking(bookingId)
}
