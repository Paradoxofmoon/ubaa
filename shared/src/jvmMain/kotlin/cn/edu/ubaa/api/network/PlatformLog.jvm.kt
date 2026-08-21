package cn.edu.ubaa.api.network

actual fun platformLog(tag: String, message: String) {
  println("$tag: $message")
}
