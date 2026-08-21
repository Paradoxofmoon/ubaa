package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.api.auth.ApiClientProvider
import cn.edu.ubaa.api.auth.safeApiCall
import cn.edu.ubaa.api.core.ApiClient
import cn.edu.ubaa.model.dto.LibBookAreaDetailDto
import cn.edu.ubaa.model.dto.LibBookAreaDto
import cn.edu.ubaa.model.dto.LibBookBookingsResponse
import cn.edu.ubaa.model.dto.LibBookCancelResponse
import cn.edu.ubaa.model.dto.LibBookLibraryDto
import cn.edu.ubaa.model.dto.LibBookReserveRequest
import cn.edu.ubaa.model.dto.LibBookReserveResponse
import cn.edu.ubaa.model.dto.LibBookSeatDto
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

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

  constructor(apiClient: ApiClient) : this({ RelayLibBookApiBackend(apiClient) })

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

internal class RelayLibBookApiBackend(private val apiClient: ApiClient = ApiClientProvider.shared) :
    LibBookApiBackend {
  override suspend fun getLibraries(day: String): Result<List<LibBookLibraryDto>> = safeApiCall {
    apiClient.getClient().get("api/v1/libbook/libraries") { parameter("day", day) }
  }

  override suspend fun getAreas(
      premisesId: String,
      storeyId: String?,
      day: String,
  ): Result<List<LibBookAreaDto>> = safeApiCall {
    apiClient.getClient().get("api/v1/libbook/areas") {
      parameter("premisesId", premisesId)
      if (!storeyId.isNullOrBlank()) parameter("storeyId", storeyId)
      parameter("day", day)
    }
  }

  override suspend fun getAreaDetail(areaId: String): Result<LibBookAreaDetailDto> = safeApiCall {
    apiClient.getClient().get("api/v1/libbook/areas/$areaId")
  }

  override suspend fun getSeats(
      areaId: String,
      day: String,
      startTime: String,
      endTime: String,
  ): Result<List<LibBookSeatDto>> = safeApiCall {
    apiClient.getClient().get("api/v1/libbook/areas/$areaId/seats") {
      parameter("day", day)
      parameter("startTime", startTime)
      parameter("endTime", endTime)
    }
  }

  override suspend fun reserve(request: LibBookReserveRequest): Result<LibBookReserveResponse> =
      safeApiCall {
        apiClient.getClient().post("api/v1/libbook/bookings") {
          contentType(ContentType.Application.Json)
          setBody(request)
        }
      }

  override suspend fun getBookings(page: Int, limit: Int): Result<LibBookBookingsResponse> =
      safeApiCall {
        apiClient.getClient().get("api/v1/libbook/reservations") {
          parameter("page", page)
          parameter("limit", limit)
        }
      }

  override suspend fun cancelBooking(bookingId: String): Result<LibBookCancelResponse> =
      safeApiCall {
        apiClient.getClient().post("api/v1/libbook/bookings/$bookingId/cancel")
      }
}
