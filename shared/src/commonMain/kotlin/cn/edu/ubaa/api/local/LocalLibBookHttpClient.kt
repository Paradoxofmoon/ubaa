package cn.edu.ubaa.api.local

import io.ktor.client.engine.HttpClientEngine

// booking.lib.buaa.edu.cn currently presents a chain that is not trusted by JVM/Android;
// keep the relaxed TLS handling scoped to the library booking client.
internal expect fun getLibBookHttpClientEngine(): HttpClientEngine
