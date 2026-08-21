package cn.edu.ubaa.utils

import kotlin.time.Duration
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class UpstreamTimeoutException(
    message: String,
    val code: String = "upstream_timeout",
    cause: Throwable? = null,
) : RuntimeException(message, cause)

suspend fun <T> withUpstreamDeadline(
    timeout: Duration,
    message: String,
    code: String = "upstream_timeout",
    block: suspend () -> T,
): T {
  return try {
    withTimeout(timeout) { block() }
  } catch (e: TimeoutCancellationException) {
    throw UpstreamTimeoutException(message, code, e)
  }
}
