package cn.edu.ubaa.ui.screens.electricity

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

internal actual fun platformEngine(): HttpClientEngine = Js.create()
