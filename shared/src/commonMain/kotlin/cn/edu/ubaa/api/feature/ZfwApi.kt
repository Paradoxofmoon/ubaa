package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.api.auth.ApiClientProvider
import cn.edu.ubaa.api.core.ApiClient
import cn.edu.ubaa.model.dto.TrafficData
import io.ktor.http.Cookie

/** 校园网自助服务门户（zfw.buaa.edu.cn）登录结果。 */
sealed class ZfwLoginResult {
  /** 登录成功，可携带已注入的 Cookies 供 WebView 使用。 */
  data class Success(val cookies: List<Cookie> = emptyList()) : ZfwLoginResult()

  /** 需要短信验证码进行二次验证。 */
  data class NeedSms(val message: String, val remainSeconds: Int?) : ZfwLoginResult()
}

/** 缴费页面预加载信息：账号、产品、CSRF 令牌。 */
data class ZfwPayPageData(
    val cardNo: String,
    val productId: String,
    val csrfParam: String,
    val csrfToken: String,
)

/** 充值提交结果。 */
sealed class ZfwPayResult {
  /** 充值成功，携带支付收银台地址与二维码图片字节。 */
  data class Success(
      val cashierUrl: String,
      val qrcodeBytes: ByteArray?,
  ) : ZfwPayResult()

  /** 充值失败，携带错误信息。 */
  data class Failure(val message: String) : ZfwPayResult()
}

/** 校园网自助服务门户 API 后端。 负责验证码获取与登录态建立。 */
interface ZfwApiBackend {
  /** 获取登录验证码图片与相关 Cookie 描述。 */
  suspend fun fetchCaptcha(): Pair<ByteArray, String>

  /**
   * 登录深澜自助服务门户。
   *
   * @param username 学工号
   * @param password 统一身份认证密码
   * @param captcha 验证码
   * @param smsCode 短信验证码（可选，当服务端要求短信验证时传入）
   * @return 登录结果，可能是成功或需要短信验证
   */
  suspend fun login(
      username: String,
      password: String,
      captcha: String,
      smsCode: String? = null,
  ): Result<ZfwLoginResult>

  /**
   * 查询当前账号的校园网流量信息。
   *
   * 需在 [login] 成功后调用（复用登录会话）。返回首页"产品信息"中的流量数据。
   */
  suspend fun getTraffic(): Result<TrafficData>

  /** 获取缴费页面预加载信息（账号、产品、CSRF）。需在 [login] 成功后调用。 */
  suspend fun fetchPayPage(): Result<ZfwPayPageData>

  /** 获取缴费验证码图片。需在 [login] 成功后调用。 */
  suspend fun fetchPayCaptcha(): Result<ByteArray>

  /**
   * 提交充值。
   *
   * @param amount 充值金额（元）
   * @param captcha 缴费验证码
   * @param payPageData 缴费页面预加载信息（含 CSRF 令牌）
   * @return 成功时携带支付收银台地址
   */
  suspend fun submitPay(
      amount: String,
      captcha: String,
      payPageData: ZfwPayPageData,
  ): Result<ZfwPayResult>
}

/** 校园网充值 API 服务入口。 根据当前连接模式自动选择直连、WebVPN 或中继后端。 */
class ZfwApi(
    private val backendProvider: () -> ZfwApiBackend = { ConnectionRuntime.apiFactory().zfwApi() }
) {
  internal constructor(backend: ZfwApiBackend) : this({ backend })

  constructor(apiClient: ApiClient) : this({ RelayZfwApiBackend(apiClient) })

  private fun currentBackend(): ZfwApiBackend = backendProvider()

  /** 获取验证码图片字节与 Cookie 描述。刷新只需再次调用本方法即可。 */
  suspend fun fetchCaptcha(): Pair<ByteArray, String> = currentBackend().fetchCaptcha()

  /**
   * 登录自助服务门户。
   *
   * @param smsCode 可选短信验证码
   */
  suspend fun login(
      username: String,
      password: String,
      captcha: String,
      smsCode: String? = null,
  ): Result<ZfwLoginResult> =
      currentBackend().login(username, password, captcha, smsCode)

  /** 查询校园网流量，需在 [login] 成功后调用。 */
  suspend fun getTraffic(): Result<TrafficData> = currentBackend().getTraffic()

  /** 获取缴费页面预加载信息。 */
  suspend fun fetchPayPage(): Result<ZfwPayPageData> = currentBackend().fetchPayPage()

  /** 获取缴费验证码图片。 */
  suspend fun fetchPayCaptcha(): Result<ByteArray> = currentBackend().fetchPayCaptcha()

  /** 提交充值。 */
  suspend fun submitPay(
      amount: String,
      captcha: String,
      payPageData: ZfwPayPageData,
  ): Result<ZfwPayResult> = currentBackend().submitPay(amount, captcha, payPageData)
}

internal class RelayZfwApiBackend(
    private val apiClient: ApiClient = ApiClientProvider.shared
) : ZfwApiBackend {
  override suspend fun fetchCaptcha(): Pair<ByteArray, String> {
    throw NotImplementedError("SERVER_RELAY 模式下校园网充值尚未实现")
  }

  override suspend fun login(
      username: String,
      password: String,
      captcha: String,
      smsCode: String?,
  ): Result<ZfwLoginResult> {
    // TODO: 实现 SERVER_RELAY 模式下的校园网充值登录中继接口
    return Result.failure(
        NotImplementedError("SERVER_RELAY 模式下校园网充值登录尚未实现")
    )
  }

  override suspend fun getTraffic(): Result<TrafficData> {
    return Result.failure(
        NotImplementedError("SERVER_RELAY 模式下校园网流量查询尚未实现")
    )
  }

  override suspend fun fetchPayPage(): Result<ZfwPayPageData> {
    return Result.failure(
        NotImplementedError("SERVER_RELAY 模式下校园网充值尚未实现")
    )
  }

  override suspend fun fetchPayCaptcha(): Result<ByteArray> {
    return Result.failure(
        NotImplementedError("SERVER_RELAY 模式下校园网充值尚未实现")
    )
  }

  override suspend fun submitPay(
      amount: String,
      captcha: String,
      payPageData: ZfwPayPageData,
  ): Result<ZfwPayResult> {
    return Result.failure(
        NotImplementedError("SERVER_RELAY 模式下校园网充值尚未实现")
    )
  }
}
