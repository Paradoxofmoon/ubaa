package cn.edu.ubaa.ui.screens.electricity

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

internal actual fun platformEngine(): HttpClientEngine = CIO.create()
