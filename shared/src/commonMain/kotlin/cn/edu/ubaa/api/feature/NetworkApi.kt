package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.model.dto.TrafficData

/** 校园网流量查询 API 服务。 */
interface NetworkApiBackend {
  /** 查询当前用户的校园网流量使用情况。 */
  suspend fun getTraffic(): Result<TrafficData>
}

/** 校园网流量查询 API 服务入口。 根据当前连接模式自动选择直连、WebVPN 或中继后端。 */
class NetworkApi(
    private val backendProvider: () -> NetworkApiBackend = {
      ConnectionRuntime.apiFactory().networkApi()
    }
) {
  internal constructor(backend: NetworkApiBackend) : this({ backend })

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
