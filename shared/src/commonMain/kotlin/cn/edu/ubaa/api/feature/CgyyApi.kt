package cn.edu.ubaa.api.feature

import cn.edu.ubaa.api.ConnectionRuntime
import cn.edu.ubaa.model.dto.CgyyBuddyListResponse
import cn.edu.ubaa.model.dto.CgyyClickWordCaptchaDto
import cn.edu.ubaa.model.dto.CgyyClickWordCheckResult
import cn.edu.ubaa.model.dto.CgyyDayInfoResponse
import cn.edu.ubaa.model.dto.CgyyLockCodeResponse
import cn.edu.ubaa.model.dto.CgyyOrderDto
import cn.edu.ubaa.model.dto.CgyyOrderPayResult
import cn.edu.ubaa.model.dto.CgyyOrdersPageResponse
import cn.edu.ubaa.model.dto.CgyyPurposeTypeDto
import cn.edu.ubaa.model.dto.CgyyReservationSubmitRequest
import cn.edu.ubaa.model.dto.CgyyReservationSubmitResponse
import cn.edu.ubaa.model.dto.CgyySportOrderSubmitRequest
import cn.edu.ubaa.model.dto.CgyySportOrderSubmitResponse
import cn.edu.ubaa.model.dto.CgyyVenueSiteDto

interface CgyyApiBackend {
  suspend fun getVenueSites(): Result<List<CgyyVenueSiteDto>>

  suspend fun getPurposeTypes(): Result<List<CgyyPurposeTypeDto>>

  suspend fun getDayInfo(venueSiteId: Int, date: String): Result<CgyyDayInfoResponse>

  suspend fun submitReservation(
      request: CgyyReservationSubmitRequest
  ): Result<CgyyReservationSubmitResponse>

  suspend fun getMyOrders(page: Int, size: Int): Result<CgyyOrdersPageResponse>

  suspend fun getOrderDetail(orderId: Int): Result<CgyyOrderDto>

  suspend fun cancelOrder(orderId: Int): Result<CgyyReservationSubmitResponse>

  suspend fun getLockCode(): Result<CgyyLockCodeResponse>

  /** 运动场点选验证码获取（clickWord）。默认不支持，由直连后端实现。 */
  suspend fun getClickWordCaptcha(): Result<CgyyClickWordCaptchaDto> =
      Result.failure(
          UnsupportedOperationException("clickWord captcha is not supported by this backend")
      )

  /** 运动场点选验证码校验：pointJson = AES-ECB(点击坐标JSON, secretKey)。 */
  suspend fun checkClickWordCaptcha(
      pointJson: String,
      token: String,
  ): Result<CgyyClickWordCheckResult> =
      Result.failure(
          UnsupportedOperationException("clickWord captcha is not supported by this backend")
      )

  /** 运动场下单提交（venue-server order/submit）。默认不支持，由直连后端实现。 */
  suspend fun submitSportOrder(
      request: CgyySportOrderSubmitRequest
  ): Result<CgyySportOrderSubmitResponse> =
      Result.failure(
          UnsupportedOperationException("sport order submit is not supported by this backend")
      )

  /** 运动场同伴列表（venue-server /api/buddies，page=0&size=20）。默认不支持，由直连后端实现。 */
  suspend fun getBuddies(page: Int = 0, size: Int = 20): Result<CgyyBuddyListResponse> =
      Result.failure(UnsupportedOperationException("buddies is not supported by this backend"))

  /** 运动场添加同伴：POST /api/buddies（buddyType=1&userUid=<学号>）。返回刷新后的列表。 */
  suspend fun addBuddy(userUid: String, buddyType: Int = 1): Result<CgyyBuddyListResponse> =
      Result.failure(UnsupportedOperationException("add buddy is not supported by this backend"))

  /** 运动场删除同伴：POST /api/buddies/del/{id}。返回刷新后的列表。 */
  suspend fun deleteBuddy(buddyId: Int): Result<CgyyBuddyListResponse> =
      Result.failure(UnsupportedOperationException("delete buddy is not supported by this backend"))

  /** 运动场订单支付：POST /api/venue/finances/order/pay → 航财通·校园付二维码。默认不支持，由直连后端实现。 */
  suspend fun paySportOrder(tradeNo: String, payType: Int = 13): Result<CgyyOrderPayResult> =
      Result.failure(UnsupportedOperationException("pay is not supported by this backend"))

  /** 运动场订单取消：POST /api/venue/finances/order/cancel（venueTradeNo）。默认不支持，由直连后端实现。 */
  suspend fun cancelSportOrder(tradeNo: String): Result<CgyyReservationSubmitResponse> =
      Result.failure(
          UnsupportedOperationException("sport order cancel is not supported by this backend")
      )
}

open class CgyyApi(
    private val backendProvider: () -> CgyyApiBackend = { ConnectionRuntime.apiFactory().cgyyApi() }
) {
  internal constructor(backend: CgyyApiBackend) : this({ backend })

  private fun currentBackend(): CgyyApiBackend = backendProvider()

  open suspend fun getVenueSites(): Result<List<CgyyVenueSiteDto>> {
    return currentBackend().getVenueSites()
  }

  open suspend fun getPurposeTypes(): Result<List<CgyyPurposeTypeDto>> {
    return currentBackend().getPurposeTypes()
  }

  open suspend fun getDayInfo(venueSiteId: Int, date: String): Result<CgyyDayInfoResponse> {
    return currentBackend().getDayInfo(venueSiteId, date)
  }

  open suspend fun submitReservation(
      request: CgyyReservationSubmitRequest
  ): Result<CgyyReservationSubmitResponse> {
    return currentBackend().submitReservation(request)
  }

  open suspend fun getMyOrders(page: Int = 0, size: Int = 20): Result<CgyyOrdersPageResponse> {
    return currentBackend().getMyOrders(page, size)
  }

  open suspend fun getOrderDetail(orderId: Int): Result<CgyyOrderDto> {
    return currentBackend().getOrderDetail(orderId)
  }

  open suspend fun cancelOrder(orderId: Int): Result<CgyyReservationSubmitResponse> {
    return currentBackend().cancelOrder(orderId)
  }

  open suspend fun getLockCode(): Result<CgyyLockCodeResponse> {
    return currentBackend().getLockCode()
  }

  open suspend fun getClickWordCaptcha(): Result<CgyyClickWordCaptchaDto> {
    return currentBackend().getClickWordCaptcha()
  }

  open suspend fun checkClickWordCaptcha(
      pointJson: String,
      token: String,
  ): Result<CgyyClickWordCheckResult> {
    return currentBackend().checkClickWordCaptcha(pointJson, token)
  }

  open suspend fun submitSportOrder(
      request: CgyySportOrderSubmitRequest
  ): Result<CgyySportOrderSubmitResponse> {
    return currentBackend().submitSportOrder(request)
  }

  open suspend fun getBuddies(page: Int = 0, size: Int = 20): Result<CgyyBuddyListResponse> {
    return currentBackend().getBuddies(page, size)
  }

  open suspend fun addBuddy(userUid: String, buddyType: Int = 1): Result<CgyyBuddyListResponse> {
    return currentBackend().addBuddy(userUid, buddyType)
  }

  open suspend fun deleteBuddy(buddyId: Int): Result<CgyyBuddyListResponse> {
    return currentBackend().deleteBuddy(buddyId)
  }

  open suspend fun paySportOrder(tradeNo: String, payType: Int = 13): Result<CgyyOrderPayResult> {
    return currentBackend().paySportOrder(tradeNo, payType)
  }

  open suspend fun cancelSportOrder(tradeNo: String): Result<CgyyReservationSubmitResponse> {
    return currentBackend().cancelSportOrder(tradeNo)
  }
}

/** 运动场（venue-server）预约 API 服务。复用 [CgyyApiBackend] 接口，只切换后端为运动场。 */
class SportVenueApi : CgyyApi({ ConnectionRuntime.apiFactory().sportVenueApi() })
