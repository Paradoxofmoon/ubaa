package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.api.auth.ApiClientProvider
import cn.edu.ubaa.api.core.ApiClient
import cn.edu.ubaa.model.dto.TrafficData

/** 校园网流量查询 API 服务。 */
interface NetworkApiBackend {
  /** 查询当前用户的校园网流量使用情况。 */
  suspend fun getTraffic(): Result<TrafficData>
}

/** 校园网流量查询 API 服务入口。 根据当前连接模式自动选择直连、WebVPN 或中继后端。 */
class NetworkApi(
    private val backendProvider: () -> NetworkApiBackend = { ConnectionRuntime.apiFactory().networkApi() }
) {
  internal constructor(backend: NetworkApiBackend) : this({ backend })

  constructor(apiClient: ApiClient) : this({ RelayNetworkApiBackend(apiClient) })

  private fun currentBackend(): NetworkApiBackend = backendProvider()

  /**
   * 查询校园网流量。
   *
   * @return 包含免费、赠送与计费流量的 [Result]。若失败则包含异常信息。
   */
  suspend fun getTraffic(): Result<TrafficData> {
    return currentBackend().getTraffic()
  }
}

internal class RelayNetworkApiBackend(
    private val apiClient: ApiClient = ApiClientProvider.shared
) : NetworkApiBackend {
  override suspend fun getTraffic(): Result<TrafficData> {
    // TODO: 实现 SERVER_RELAY 模式下的校园网流量查询中继接口
    return Result.failure(
        NotImplementedError("SERVER_RELAY 模式下校园网流量查询尚未实现")
    )
  }
}
