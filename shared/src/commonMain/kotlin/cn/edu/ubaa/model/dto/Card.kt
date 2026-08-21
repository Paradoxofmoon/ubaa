package cn.edu.ubaa.model.dto

import kotlinx.serialization.Serializable

/**
 * 校园一卡通余额查询响应。
 *
 * @property success 请求是否成功。
 * @property data 余额数据。
 */
@Serializable
data class CardBalanceResponse(
    val success: Boolean = false,
    val data: CardBalanceData? = null
)

/**
 * 校园一卡通余额数据。
 *
 * @property balance 账户余额。
 * @property unclaimedAmount 待领取金额。
 */
@Serializable
data class CardBalanceData(
    val balance: String = "0.00",
    val unclaimedAmount: String = "0.00"
)
