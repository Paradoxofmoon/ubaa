package cn.edu.ubaa.api.local

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

internal actual fun getLibBookHttpClientEngine(): HttpClientEngine =
    CIO.create {
      https {
        trustManager =
            object : X509TrustManager {
              override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) =
                  Unit

              override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) =
                  Unit

              override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
      }
    }
