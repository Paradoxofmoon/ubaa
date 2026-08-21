package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.api.auth.ApiClientProvider
import cn.edu.ubaa.api.core.ApiClient
import cn.edu.ubaa.model.dto.CardBalanceData

/** 校园卡充值支付方式。 */
data class CardPayWay(
    val id: String,
    val name: String,
    val text: String,
    val channel: String, // 如 wxpay/alipay/ylpay
)

/** 校园卡充值发起支付的结果。 */
data class CardRechargeResult(
    val payUrl: String? = null,
    val payQrCode: String? = null,
    val payWebForm: String? = null,
    val cashierUrl: String? = null,
)

/** 校园一卡通（校园卡）API 服务。 提供校园卡余额查询、充值等功能。 */
interface CardApiBackend {
  /** 查询当前用户的一卡通余额。 */
  suspend fun getBalance(): Result<CardBalanceData>

  /** 查询校园卡充值可用的支付方式列表。默认返回空则使用兜底。 */
  suspend fun getRechargePayWays(): Result<List<CardPayWay>> = Result.success(emptyList())

  /**
   * 校园卡充值：创建订单并发起支付。
   *
   * @param amount 充值金额（元），需在账单项 minAmount~maxAmount 内。
   * @param payWayId 支付方式 id（来自 [getRechargePayWays]）。
   * @return 支付跳转地址（如 weixin:// 或 alipays://）。
   */
  suspend fun beginRecharge(amount: String, payWayId: String): Result<CardRechargeResult>
}

/** 校园一卡通 API 服务入口。 根据当前连接模式自动选择直连、WebVPN 或中继后端。 */
class CardApi(
    private val backendProvider: () -> CardApiBackend = { ConnectionRuntime.apiFactory().cardApi() }
) {
  internal constructor(backend: CardApiBackend) : this({ backend })

  constructor(apiClient: ApiClient) : this({ RelayCardApiBackend(apiClient) })

  private fun currentBackend(): CardApiBackend = backendProvider()

  /**
   * 查询校园卡余额。
   *
   * @return 包含余额与待领取金额的 [Result]。若失败则包含异常信息。
   */
  suspend fun getBalance(): Result<CardBalanceData> {
    return currentBackend().getBalance()
  }

  /** 查询校园卡充值可用的支付方式列表。 */
  suspend fun getRechargePayWays(): Result<List<CardPayWay>> {
    return currentBackend().getRechargePayWays()
  }

  /** 校园卡充值：创建订单并发起支付，返回支付跳转地址。 */
  suspend fun beginRecharge(amount: String, payWayId: String): Result<CardRechargeResult> {
    return currentBackend().beginRecharge(amount, payWayId)
  }
}

internal class RelayCardApiBackend(
    private val apiClient: ApiClient = ApiClientProvider.shared
) : CardApiBackend {
  override suspend fun getBalance(): Result<CardBalanceData> {
    // TODO: 实现 SERVER_RELAY 模式下的一卡通余额查询中继接口
    return Result.failure(
        NotImplementedError("SERVER_RELAY 模式下一卡通余额查询尚未实现")
    )
  }

  override suspend fun getRechargePayWays(): Result<List<CardPayWay>> {
    return Result.failure(
        NotImplementedError("SERVER_RELAY 模式下校园卡充值支付方式查询尚未实现")
    )
  }

  override suspend fun beginRecharge(amount: String, payWayId: String): Result<CardRechargeResult> {
    return Result.failure(
        NotImplementedError("SERVER_RELAY 模式下校园卡充值尚未实现")
    )
  }
}
