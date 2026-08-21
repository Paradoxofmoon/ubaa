package cn.edu.ubaa.api.network

import cn.edu.ubaa.api.local.LocalUpstreamClientProvider
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*

/**
 * 轻量 HTTP 客户端注册中心。
 *
 * 用于不需要三模式（DIRECT/WEBVPN/SERVER_RELAY）支持的功能。
 * 相对于 ApiFactory 的简化替代——直接返回配置好的 Ktor HttpClient。
 */
object ServiceRegistry {

  /**
   * 创建全新的隔离 HTTP 客户端。
   * 使用内存 Cookie（不落盘），类似浏览器无痕模式。
   * 调用方负责在使用完毕后调用 [HttpClient.close]。
   */
  fun freshClient(): HttpClient =
      LocalUpstreamClientProvider.newClient(
          cookieStorage = AcceptAllCookiesStorage(),
          followRedirects = true,
      )
}
