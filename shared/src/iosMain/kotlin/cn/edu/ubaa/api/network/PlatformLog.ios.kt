package cn.edu.ubaa.api.network

import platform.Foundation.NSLog

actual fun platformLog(tag: String, message: String) {
  NSLog("[$tag] $message")
}
