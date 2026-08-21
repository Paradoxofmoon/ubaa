package cn.edu.ubaa.cgyy

import java.security.MessageDigest

class CgyySigner(
    private val prefix: String = PREFIX,
    val appKey: String = APP_KEY,
) {
  fun sign(path: String, params: Map<String, Any?>, timestamp: Long): String {
    val normalizedPath = if (path.startsWith("/")) path else "/$path"
    val cleaned = cleanParams(params)
    val payload = buildString {
      append(prefix)
      append(normalizedPath)
      cleaned.keys.sorted().forEach { key ->
        val value = cleaned.getValue(key)
        if (isPrimitiveForSign(value)) {
          append(key)
          append(value.toString())
        }
      }
      append(timestamp)
      append(' ')
      append(prefix)
    }
    return md5(payload)
  }

  fun cleanParams(params: Map<String, Any?>): Map<String, Any?> {
    return params.filterKeys { it !in REMOVE_KEYS }.filterValues { isPrimitiveForSign(it) }
  }

  fun addNoCacheIfMissing(params: Map<String, Any?>, timestamp: Long): Map<String, Any?> {
    if ("nocache" in params) return params
    return params + ("nocache" to timestamp)
  }

  private fun isPrimitiveForSign(value: Any?): Boolean {
    return when (value) {
      null -> false
      is String -> value.isNotEmpty()
      is Iterable<*> -> false
      is Array<*> -> false
      is Map<*, *> -> false
      else -> true
    }
  }

  private fun md5(text: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  companion object {
    const val PREFIX = "c640ca392cd45fb3a55b00a63a86c618"
    const val APP_KEY = "8fceb735082b5a529312040b58ea780b"

    private val REMOVE_KEYS =
        setOf("gmtCreate", "gmtModified", "creator", "modifier", "id", "_index", "_rowKey")
  }
}
