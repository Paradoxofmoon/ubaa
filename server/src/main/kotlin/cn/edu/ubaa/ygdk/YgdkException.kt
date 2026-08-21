package cn.edu.ubaa.ygdk

open class YgdkException(
    message: String,
    val code: String = "ygdk_error",
    cause: Throwable? = null,
) : Exception(message, cause)

class YgdkAuthenticationException(
    message: String = "阳光打卡登录已失效，请重新登录 UBAA",
) : YgdkException(message, code = "unauthenticated")
