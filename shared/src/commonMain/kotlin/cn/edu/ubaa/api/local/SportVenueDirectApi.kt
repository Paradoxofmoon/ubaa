package cn.edu.ubaa.api.local

import cn.edu.ubaa.api.feature.CgyyApi

/**
 * 场馆预约固定直连后端。
 *
 * 绕过连接模式选择（DIRECT / WEBVPN / SERVER_RELAY），强制直连 cgyy 运动场
 * （venue-zhjs-server），不经过 `ubaa.mofrp.top` 之类的外部中转服务器——该服务器并非
 * 用户自有，场馆原生流程不应依赖它。
 *
 * `LocalCgyyApiBackend` 与 `CgyyApi(backend)` 均为 shared 模块 internal，因此必须在此
 * 提供公开工厂，供 composeApp 使用。
 */
fun sportVenueDirectApi(): CgyyApi = CgyyApi(LocalCgyyApiBackend(sportVenue = true))
