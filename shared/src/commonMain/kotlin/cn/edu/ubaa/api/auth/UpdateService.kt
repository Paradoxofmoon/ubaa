package cn.edu.ubaa.api.auth

import cn.edu.ubaa.BuildKonfig
import cn.edu.ubaa.api.core.ApiClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
enum class AppUpdateStatus {
  UP_TO_DATE,
  UPDATE_AVAILABLE,
  UNKNOWN_LATEST_VERSION,
}

@Serializable
data class AppVersionCheckResponse(
    val latestVersion: String,
    val status: AppUpdateStatus,
    val updateAvailable: Boolean,
    val downloadUrl: String,
    val releaseNotes: String? = null,
    val serverVersion: String? = null,
    val aligned: Boolean? = null,
)

/**
 * 更新检测服务。 通过可配置的服务端检查客户端是否存在新版本。
 *
 * 开源版未配置 `API_ENDPOINT` 时自动禁用（返回 null），不依赖任何第三方服务器； 自建服务端者通过构建时 `API_ENDPOINT` 环境变量启用。
 */
class UpdateService(private val apiClientProvider: () -> ApiClient = { ApiClientProvider.shared }) {
  constructor(apiClient: ApiClient) : this({ apiClient })

  /** 检查当前客户端是否需要更新。 服务端未配置时返回 null（视为无更新）。 */
  suspend fun checkUpdate(clientVersion: String = BuildKonfig.VERSION): AppVersionCheckResponse? {
    if (BuildKonfig.API_ENDPOINT.isBlank()) {
      return null
    }
    return try {
      val apiClient = apiClientProvider()
      val response =
          apiClient.getClient().get("api/v1/app/version") {
            parameter("clientVersion", clientVersion)
          }
      if (response.status != HttpStatusCode.OK) {
        return null
      }
      response.body<AppVersionCheckResponse>().takeIf {
        it.status == AppUpdateStatus.UPDATE_AVAILABLE
      }
    } catch (e: Throwable) {
      if (e is CancellationException) throw e
      null
    }
  }
}
