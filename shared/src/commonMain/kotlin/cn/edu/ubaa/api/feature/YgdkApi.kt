package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.model.dto.YgdkClockinSubmitRequest
import cn.edu.ubaa.model.dto.YgdkClockinSubmitResponse
import cn.edu.ubaa.model.dto.YgdkOverviewResponse
import cn.edu.ubaa.model.dto.YgdkRecordsPageResponse

interface YgdkApiBackend {
  suspend fun getOverview(): Result<YgdkOverviewResponse>

  suspend fun getRecords(page: Int, size: Int): Result<YgdkRecordsPageResponse>

  suspend fun submitClockin(request: YgdkClockinSubmitRequest): Result<YgdkClockinSubmitResponse>
}

open class YgdkApi(
    private val backendProvider: () -> YgdkApiBackend = { ConnectionRuntime.apiFactory().ygdkApi() }
) {
  internal constructor(backend: YgdkApiBackend) : this({ backend })

  private fun currentBackend(): YgdkApiBackend = backendProvider()

  open suspend fun getOverview(): Result<YgdkOverviewResponse> {
    return currentBackend().getOverview()
  }

  open suspend fun getRecords(page: Int = 1, size: Int = 20): Result<YgdkRecordsPageResponse> {
    return currentBackend().getRecords(page, size)
  }

  open suspend fun submitClockin(
      request: YgdkClockinSubmitRequest
  ): Result<YgdkClockinSubmitResponse> {
    return currentBackend().submitClockin(request)
  }
}
