package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.model.dto.BusBuyResultDto
import cn.edu.ubaa.model.dto.BusIndexPageDto
import cn.edu.ubaa.model.dto.BusSessionUserDto
import cn.edu.ubaa.model.dto.BusShiftsResponse
import cn.edu.ubaa.model.dto.BusTicketDetailDto

/** 智慧校车订票后端接口（直达直连 + 中继两套实现）。 */
interface BusApiBackend {
  suspend fun getIndexPage(): Result<BusIndexPageDto>

  suspend fun getSessionUser(): Result<BusSessionUserDto>

  suspend fun searchShifts(
      origin: String,
      terminal: String,
      date: String,
  ): Result<BusShiftsResponse>

  suspend fun getTicketDetail(date: String, shiftsNumber: String): Result<BusTicketDetailDto>

  suspend fun getCaptchaImage(): Result<ByteArray>

  suspend fun buyTicket(
      date: String,
      shiftsNumber: String,
      checkStr: String,
      csrfToken: String,
  ): Result<BusBuyResultDto>
}

open class BusApi(
    private val backendProvider: () -> BusApiBackend = { ConnectionRuntime.apiFactory().busApi() }
) {
  internal constructor(backend: BusApiBackend) : this({ backend })

  private fun currentBackend(): BusApiBackend = backendProvider()

  open suspend fun getIndexPage(): Result<BusIndexPageDto> = currentBackend().getIndexPage()

  open suspend fun getSessionUser(): Result<BusSessionUserDto> = currentBackend().getSessionUser()

  open suspend fun searchShifts(
      origin: String,
      terminal: String,
      date: String,
  ): Result<BusShiftsResponse> = currentBackend().searchShifts(origin, terminal, date)

  open suspend fun getTicketDetail(
      date: String,
      shiftsNumber: String,
  ): Result<BusTicketDetailDto> = currentBackend().getTicketDetail(date, shiftsNumber)

  open suspend fun getCaptchaImage(): Result<ByteArray> = currentBackend().getCaptchaImage()

  open suspend fun buyTicket(
      date: String,
      shiftsNumber: String,
      checkStr: String,
      csrfToken: String,
  ): Result<BusBuyResultDto> = currentBackend().buyTicket(date, shiftsNumber, checkStr, csrfToken)
}

internal data class BusBuyRequest(
    val date: String,
    val shiftsNumber: String,
    val checkStr: String,
)
