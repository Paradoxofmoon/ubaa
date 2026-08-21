package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.auth.ApiCallException
import cn.edu.ubaa.api.auth.toUserFacingApiException
import cn.edu.ubaa.api.auth.userFacingMessageForCode
import cn.edu.ubaa.api.feature.NetworkApiBackend
import cn.edu.ubaa.api.storage.CredentialStore
import cn.edu.ubaa.api.network.DebugFileSink
import cn.edu.ubaa.model.dto.TrafficData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal class LocalNetworkApiBackend : NetworkApiBackend {
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun getTraffic(): Result<TrafficData> {
    val username = CredentialStore.getUsername()
    val password = CredentialStore.getPassword()
    if (username.isNullOrBlank() || password.isNullOrBlank()) {
      return Result.failure(localUnauthenticatedApiException())
    }

    return try {
      appLogin(username, password)
      val response =
          LocalUpstreamClientProvider.shared()
              .get(
                  localUpstreamUrl(
                      "https://app.buaa.edu.cn/buaanet/wap/default/index"
                  )
              ) {
                header(
                    HttpHeaders.Accept,
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                )
              }

      parseTrafficResponse(response)
    } catch (e: Exception) {
      Result.failure(e.toUserFacingApiException("校园网流量查询失败，请稍后重试"))
    }
  }

  private suspend fun appLogin(username: String, password: String) {
    val response =
        LocalUpstreamClientProvider.shared()
            .post(
                localUpstreamUrl(
                    "https://app.buaa.edu.cn/uc/wap/login/check"
                )
            ) {
              header(HttpHeaders.Accept, "application/json, text/plain, */*")
              header("X-Requested-With", "XMLHttpRequest")
              setBody(
                  FormDataContent(
                      Parameters.build {
                        append("username", username)
                        append("password", password)
                      }
                  )
              )
            }

    val body = response.bodyAsText()
    if (response.status != HttpStatusCode.OK) {
      throw ApiCallException(
          message = "校园网登录失败，请稍后重试",
          status = response.status,
          code = "network_error",
      )
    }

    val payload =
        runCatching { json.decodeFromString<JsonObject>(body) }.getOrElse {
          throw ApiCallException(
              message = "校园网登录响应解析失败",
              status = HttpStatusCode.BadGateway,
              code = "network_error",
          )
        }

    if (!isLoginSuccess(payload)) {
      throw ApiCallException(
          message = "校园网登录失败，请检查账号密码",
          status = HttpStatusCode.Unauthorized,
          code = "unauthenticated",
      )
    }
  }

  private suspend fun parseTrafficResponse(response: HttpResponse): Result<TrafficData> {
    val body = response.bodyAsText()
    DebugFileSink.write("traffic_page.html", body)
    DebugFileSink.write("traffic_page_len.txt", "len=${body.length} status=${response.status}")
    if (isNetworkSessionExpired(response, body)) {
      return Result.failure(resolveLocalBusinessAuthenticationFailure("network_error"))
    }
    if (response.status != HttpStatusCode.OK) {
      return Result.failure(
          localBusinessApiException(
              "network_error",
              userFacingMessageForCode("network_error", response.status),
              response.status,
          )
      )
    }

    return runCatching { extractTrafficData(body) }
        .fold(
            onSuccess = { Result.success(it) },
            onFailure = {
              Result.failure(
                  localBusinessApiException(
                      "network_error",
                      "校园网流量数据解析失败",
                      HttpStatusCode.BadGateway,
                  )
              )
            },
        )
  }

  private fun isLoginSuccess(payload: JsonObject): Boolean {
    val primitive = payload["e"]?.jsonPrimitive ?: return false
    primitive.intOrNull?.let { return it == 0 }
    return primitive.contentOrNull == "0"
  }

  private fun isNetworkSessionExpired(response: HttpResponse, body: String): Boolean {
    if (response.status == HttpStatusCode.Unauthorized) return true
    if (localIsSsoUrl(response.call.request.url.toString())) return true
    val trimmed = body.trimStart()
    return trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) &&
        (body.contains("input name=\"execution\"") ||
            body.contains("统一身份认证", ignoreCase = true))
  }

  private fun extractTrafficData(html: String): TrafficData {
    val free = extractSectionNumbers(html, "免费流量")
    val gift = extractSectionNumbers(html, "赠送流量")
    val paid = extractSectionNumbers(html, "计费流量")

    val freeTotal = free.getOrNull(0) ?: 0.0
    val freeRemaining = free.getOrNull(2) ?: free.getOrNull(1) ?: 0.0

    val giftTotal = gift.getOrNull(0)
    val giftRemaining = gift.getOrNull(2) ?: gift.getOrNull(1)

    val paidTraffic = paid.firstOrNull()

    return TrafficData(
        freeTrafficTotal = freeTotal,
        freeTrafficRemaining = freeRemaining,
        giftTrafficTotal = giftTotal,
        giftTrafficRemaining = giftRemaining,
        paidTraffic = paidTraffic,
    )
  }

  private fun extractSectionNumbers(html: String, label: String): List<Double> {
    val sectionRegex =
        Regex(
            "<div[^>]*class=\"[^\"]*btn[^\"]*\"[^>]*>[\\s\\S]{0,200}?$label[\\s\\S]{0,800}?</div>",
            RegexOption.IGNORE_CASE,
        )
    val section = sectionRegex.find(html)?.value ?: return emptyList()
    return numberGbRegex.findAll(section).mapNotNull { match ->
      match.groupValues.getOrNull(1)?.toDoubleOrNull()
    }.toList()
  }

  companion object {
    private val numberGbRegex =
        Regex("""(\d+(?:\.\d+)?)\s*[Gg][Bb]""", RegexOption.IGNORE_CASE)
  }
}
