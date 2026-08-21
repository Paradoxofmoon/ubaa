package cn.edu.ubaa.api

import cn.edu.ubaa.api.core.DefaultApiFactory
import cn.edu.ubaa.api.feature.LibBookApi
import cn.edu.ubaa.api.local.LocalAuthServiceBackend
import cn.edu.ubaa.api.local.LocalAuthSessionStore
import cn.edu.ubaa.api.local.LocalCookieStore
import cn.edu.ubaa.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.api.storage.CredentialStore
import com.russhwolf.settings.MapSettings
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue

class LocalLibBookRealIntegrationTest {
  @Test
  fun `real local direct account can login to library booking and fetch libraries`() = runTest {
    assumeTrue(System.getenv("UBAA_REAL_LIBBOOK_TEST") == "true")
    val credentials = loadRealLibBookCredentials()

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

    val librariesResult = LibBookApi().getLibraries(LocalDate.now().toString())
    assertTrue(librariesResult.isSuccess, librariesResult.exceptionOrNull()?.message.orEmpty())
    val libraries = librariesResult.getOrThrow()
    println("REAL_LOCAL_LIBBOOK libraries=${libraries.size}")
    assertTrue(libraries.isNotEmpty(), "real local account should fetch library buildings")
  }

  private fun loadRealLibBookCredentials(): RealLibBookCredentials {
    val propertiesPath =
        listOf(Path.of("local.properties"), Path.of("../local.properties"))
            .firstOrNull(Files::exists) ?: Path.of("local.properties")
    assumeTrue(
        "local.properties is required for real local libbook test",
        Files.exists(propertiesPath),
    )
    val properties = Properties()
    Files.newInputStream(propertiesPath).use(properties::load)
    val username = properties.getProperty("testuser").orEmpty().trim()
    val password = properties.getProperty("testpasswd").orEmpty().trim()
    assumeTrue(
        "testuser/testpasswd are required for real local libbook test",
        username.isNotBlank() && password.isNotBlank(),
    )
    return RealLibBookCredentials(username, password)
  }

  private data class RealLibBookCredentials(val username: String, val password: String)
}
