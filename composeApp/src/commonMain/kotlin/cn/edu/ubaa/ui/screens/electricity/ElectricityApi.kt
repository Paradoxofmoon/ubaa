package cn.edu.ubaa.ui.screens.electricity

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** shsd.buaa.edu.cn 电费购电后端。直连 HTTP，不依赖 shared 抽象层。 */

private const val BASE_URL = "https://shsd.buaa.edu.cn"

// ===== DTO =====

/** QueryIdData 返回的级联树叶子节点（电表）。字段与 shsd 实际返回严格对齐。 */
@Serializable
data class ElectricityMeter(
    val id: Int = 0,
    @SerialName("identityNo") val identityNo: String = "",
    val name: String = "",
    val address: String = "",
    @SerialName("meterNo") val meterNo: String = "",
    val campus: String = "",
    val building: String = "",
    val floor: String = "",
    val room: String = "",
)

/** /BuaaPay/Meter 返回的电表信息。字段与 shsd 实际返回严格对齐。 */
@Serializable
data class ElectricityMeterInfo(
    val id: Int = 0,
    @SerialName("meterNo") val meterNo: String? = null,
    val compus: String? = null,
    val building: String? = null,
    val room: String? = null,
    val name: String? = null,
    val address: String? = null,
    /** 上次加电状态：<0 或 >2 为未知。 */
    val payStatus: Int = -1,
    val readingTime: String? = null,
    /** 剩余电量（度）。实际返回 int。 */
    val remain: Int = 0,
    /** 表计倍率 / 最小下发电量单位（整数度）。 */
    val ct: Int = 1,
    /** 电价（元/度）。 */
    val price: Double = 0.0,
    val serial: String? = null,
    /** 存在未完成订单时返回，否则 null。 */
    val payUrl: String? = null,
    val money: Double = 0.0,
)

/** 支付下单结果。 */
sealed class ElectricityPayResult {
  data class Success(val payUrl: String) : ElectricityPayResult()
  data class Failure(val message: String) : ElectricityPayResult()
}

/** 电费后端异常。 */
class ElectricityException(message: String) : Exception(message)

// ===== 后端 =====

/** 电费购电 HTTP 后端。shsd 无需登录态，普通 GET/POST 即可。 */
class ElectricityApi(private val engine: HttpClientEngine? = null) {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private val client: HttpClient =
      HttpClient(engine ?: platformEngine()) {
        expectSuccess = false
        install(ContentNegotiation) { json(this@ElectricityApi.json) }
        install(HttpTimeout) {
          requestTimeoutMillis = 30_000
          connectTimeoutMillis = 10_000
          socketTimeoutMillis = 30_000
        }
      }

  /** 拉取全校「校区→楼宇→楼层→房间→电表」级联数据。 */
  suspend fun fetchMeterTree(refresh: Boolean = false): List<ElectricityMeter> {
    val response =
        client.get("$BASE_URL/PubBuaa/QueryIdData") {
          parameter("refresh", refresh)
          header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
        }
    if (response.status != HttpStatusCode.OK) {
      throw ElectricityException("用电查询数据加载失败（${response.status.value}）")
    }
    val body = response.bodyAsText()
    return try {
      json.decodeFromString<List<ElectricityMeter>>(body)
    } catch (e: Exception) {
      throw ElectricityException("用电查询数据解析失败: ${e.message ?: e::class.simpleName}. 前200字节=${body.take(200)}")
    }
  }

  /** 查询电表信息与余额。 */
  suspend fun fetchMeterInfo(meterId: String): ElectricityMeterInfo {
    val response =
        client.get("$BASE_URL/BuaaPay/Meter") {
          parameter("id", meterId)
          header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
        }
    if (response.status != HttpStatusCode.OK) {
      throw ElectricityException("查询电表失败（${response.status.value}）：${response.bodyAsText().take(80)}")
    }
    val body = response.bodyAsText()
    return try {
      json.decodeFromString<ElectricityMeterInfo>(body)
    } catch (e: Exception) {
      throw ElectricityException("查询电表返回格式异常: ${e.message ?: e::class.simpleName}. 原始=${body.take(300)}")
    }
  }

  /**
   * 创建购电订单，返回支付跳转地址。
   *
   * @param meterId 电表 id（/BuaaPay/Meter 返回的 id）。
   * @param writePower 下发电量（整数度，必须 >= 1）。
   */
  suspend fun submitPay(meterId: Int, writePower: Int): ElectricityPayResult {
    val response =
        client.submitForm(
            url = "$BASE_URL/BuaaPay/Pay",
            formParameters =
                Parameters.build {
                  append("id", meterId.toString())
                  append("writePower", writePower.toString())
                },
        ) {
          header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
        }
    if (response.status != HttpStatusCode.OK) {
      return ElectricityPayResult.Failure("下单失败（${response.status.value}）：${response.bodyAsText().take(80)}")
    }
    val payUrl = response.bodyAsText().trim().trim('"', '\'')
    return if (payUrl.startsWith("http://") || payUrl.startsWith("https://")) {
      ElectricityPayResult.Success(payUrl)
    } else {
      ElectricityPayResult.Failure(payUrl.ifBlank { "下单失败，请稍后重试" })
    }
  }

  /** 取消未完成的支付订单。 */
  suspend fun cancelPay(id: Int, serial: String) {
    val response =
        client.delete("$BASE_URL/BuaaPay/CancelPay") {
          parameter("id", id)
          parameter("serial", serial)
        }
    if (response.status != HttpStatusCode.OK) {
      throw ElectricityException("取消订单失败（${response.status.value}）")
    }
  }

  fun close() {
    client.close()
  }
}

/** 各平台 Ktor 引擎。 */
internal expect fun platformEngine(): HttpClientEngine
