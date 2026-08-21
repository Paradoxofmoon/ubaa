package cn.edu.ubaa.api.local

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

internal actual fun getLibBookHttpClientEngine(): HttpClientEngine = Js.create()
