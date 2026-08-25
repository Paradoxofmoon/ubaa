package cn.edu.ubaa.api.auth

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.api.ResettableSharedInstance
import cn.edu.ubaa.api.core.ApiClient
import cn.edu.ubaa.api.storage.StoredAuthTokens
import cn.edu.ubaa.model.dto.*
import kotlinx.serialization.Serializable

/** 认证服务提供者，管理全局共享的 ApiClient。 */
object ApiClientProvider {
  private val sharedClient =
      ResettableSharedInstance(factory = ::ApiClient, disposer = ApiClient::close)

  /** 全局共享的 ApiClient 实例。 */
  val shared: ApiClient
    get() = sharedClient.getOrCreate()

  fun reset() = sharedClient.reset()
}

/**
 * 会话状态响应。
 *
 * @property user 用户基本身份信息。
 * @property lastActivity 最后活动时间。
 * @property authenticatedAt 认证时间。
 */
@Serializable
data class SessionStatusResponse(
    val user: UserData,
    val lastActivity: String,
    val authenticatedAt: String,
)

interface AuthServiceBackend {
  fun hasPersistedSession(): Boolean

  fun applyStoredSession()

  fun clearStoredSession()

  suspend fun preloadLoginState(): Result<LoginPreloadResponse>

  suspend fun login(
      username: String,
      password: String,
      captcha: String?,
      execution: String?,
  ): Result<LoginResponse>

  suspend fun getAuthStatus(): Result<SessionStatusResponse>

  suspend fun logout(): Result<Unit>
}

interface UserServiceBackend {
  suspend fun getUserInfo(): Result<UserInfo>
}

/**
 * 认证服务，负责登录、预加载、注销及会话状态查询。
 *
 * @param apiClient 使用的 ApiClient 实例，默认为单例 shared。
 */
open class AuthService(
    private val backendProvider: () -> AuthServiceBackend = {
      ConnectionRuntime.apiFactory().authService()
    }
) {
  internal constructor(backend: AuthServiceBackend) : this({ backend })

  private fun currentBackend(): AuthServiceBackend = backendProvider()

  open fun hasPersistedSession(): Boolean = currentBackend().hasPersistedSession()

  /** 将本地存储的令牌应用到当前 ApiClient 中。 */
  open fun applyStoredTokens() {
    currentBackend().applyStoredSession()
  }

  open fun clearStoredSession() {
    currentBackend().clearStoredSession()
  }

  /**
   * 预加载登录状态。 为当前客户端创建或关联专属会话，并获取登录所需的附加信息（如验证码）。
   *
   * @return 预加载结果，包含验证码信息或已登录的令牌。
   */
  open suspend fun preloadLoginState(): Result<LoginPreloadResponse> {
    return currentBackend().preloadLoginState()
  }

  /**
   * 执行用户登录。 使用 preload 时创建的会话和执行标识（execution）进行认证。
   *
   * @param username 用户名（学号）。
   * @param password 密码。
   * @param captcha 验证码（如果需要）。
   * @param execution SSO 流程执行标识。
   * @return 登录结果，成功则返回 LoginResponse 并自动更新 ApiClient 令牌。
   */
  open suspend fun login(
      username: String,
      password: String,
      captcha: String? = null,
      execution: String? = null,
  ): Result<LoginResponse> {
    return currentBackend().login(username, password, captcha, execution)
  }

  /**
   * 查询当前会话状态。
   *
   * @return 包含用户信息和活动时间的 SessionStatusResponse。
   */
  open suspend fun getAuthStatus(): Result<SessionStatusResponse> {
    return currentBackend().getAuthStatus()
  }

  /**
   * 注销当前用户。 会尝试通知服务端和上游 SSO 注销，并清理本地令牌和 ApiClient 状态。
   *
   * @return 注销操作结果。
   */
  open suspend fun logout(): Result<Unit> {
    return currentBackend().logout()
  }
}

private fun LoginResponse.toStoredAuthTokens(): StoredAuthTokens =
    StoredAuthTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        accessTokenExpiresAt = accessTokenExpiresAt,
        refreshTokenExpiresAt = refreshTokenExpiresAt,
    )

private fun LoginPreloadResponse.toStoredAuthTokensOrNull(): StoredAuthTokens? {
  val currentAccessToken = accessToken ?: return null
  val currentRefreshToken = refreshToken ?: return null
  return StoredAuthTokens(
      accessToken = currentAccessToken,
      refreshToken = currentRefreshToken,
      accessTokenExpiresAt = accessTokenExpiresAt,
      refreshTokenExpiresAt = refreshTokenExpiresAt,
  )
}

/**
 * 用户相关服务，负责获取用户的详细考籍或个人信息。
 *
 * @param apiClient 使用的 ApiClient 实例。
 */
open class UserService(
    private val backendProvider: () -> UserServiceBackend = {
      ConnectionRuntime.apiFactory().userService()
    }
) {
  internal constructor(backend: UserServiceBackend) : this({ backend })

  private fun currentBackend(): UserServiceBackend = backendProvider()

  /**
   * 获取当前用户的详细资料信息。
   *
   * @return 包含用户姓名、学号等信息的 UserInfo。
   */
  open suspend fun getUserInfo(): Result<UserInfo> {
    return currentBackend().getUserInfo()
  }
}
