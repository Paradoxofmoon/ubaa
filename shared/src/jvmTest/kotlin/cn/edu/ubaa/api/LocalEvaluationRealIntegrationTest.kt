package cn.edu.ubaa.api

import cn.edu.ubaa.api.core.DefaultApiFactory
import cn.edu.ubaa.api.feature.EvaluationService
import cn.edu.ubaa.api.local.LocalAuthServiceBackend
import cn.edu.ubaa.api.local.LocalAuthSessionStore
import cn.edu.ubaa.api.local.LocalCookieStore
import cn.edu.ubaa.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.api.storage.CredentialStore
import com.russhwolf.settings.MapSettings
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue

class LocalEvaluationRealIntegrationTest {
  @Test
  fun `real local direct account can fetch evaluation list read only`() = runTest {
    assumeTrue(System.getenv("UBAA_REAL_EVALUATION_TEST") == "true")
    val credentials = loadRealEvaluationCredentials()

    ConnectionModeStore.settings = MapSettings()
    LocalAuthSessionStore.settings = MapSettings()
    LocalCookieStore.settings = MapSettings()
    CredentialStore.settings = MapSettings()
    ConnectionRuntime.clearSelectedMode()
    ConnectionRuntime.switchMode(ConnectionMode.DIRECT)
    ConnectionRuntime.apiFactoryProvider = { DefaultApiFactory }
    LocalUpstreamClientProvider.reset()

    val loginResult =
        LocalAuthServiceBackend().login(credentials.username, credentials.password, null, null)
    assertTrue(loginResult.isSuccess, loginResult.exceptionOrNull()?.message.orEmpty())

    val evaluationsResult = EvaluationService().getAllEvaluations()
    assertTrue(evaluationsResult.isSuccess, evaluationsResult.exceptionOrNull()?.message.orEmpty())
    val evaluations = evaluationsResult.getOrThrow()
    println(
        "REAL_LOCAL_EVALUATION courses=${evaluations.courses.size} " +
            "pending=${evaluations.progress.pendingCourses} " +
            "evaluated=${evaluations.progress.evaluatedCourses}"
    )
    evaluations.courses.firstOrNull()?.let { course ->
      println(
          "REAL_LOCAL_EVALUATION first_course=" +
              "${course.kcmc}/${course.bpmc}/evaluated=${course.isEvaluated}"
      )
    }
  }

  private fun loadRealEvaluationCredentials(): RealEvaluationCredentials {
    val propertiesPath =
        listOf(Path.of("local.properties"), Path.of("../local.properties"))
            .firstOrNull(Files::exists) ?: Path.of("local.properties")
    assumeTrue(
        "local.properties is required for real local evaluation test",
        Files.exists(propertiesPath),
    )
    val properties = Properties()
    Files.newInputStream(propertiesPath).use(properties::load)
    val username = properties.getProperty("testuser").orEmpty().trim()
    val password = properties.getProperty("testpasswd").orEmpty().trim()
    assumeTrue(
        "testuser/testpasswd are required for real local evaluation test",
        username.isNotBlank() && password.isNotBlank(),
    )
    return RealEvaluationCredentials(username, password)
  }

  private data class RealEvaluationCredentials(val username: String, val password: String)
}
