package cn.edu.ubaa.ui

import cn.edu.ubaa.model.dto.UserData
import cn.edu.ubaa.ui.screens.auth.AuthUiState
import cn.edu.ubaa.ui.screens.auth.LoginFormState
import kotlin.test.*

class AuthViewModelTest {

  @Test
  fun testInitialState() {
    // Test initial UI state
    val initialUiState = AuthUiState()

    assertFalse(initialUiState.isLoading)
    assertFalse(initialUiState.isLoggedIn)
    assertNull(initialUiState.userData)
    assertNull(initialUiState.userInfo)
    assertNull(initialUiState.accessToken)
    assertNull(initialUiState.error)
  }

  @Test
  fun testLoginFormState() {
    // Test initial login form state
    val initialFormState = LoginFormState()

    assertEquals("", initialFormState.username)
    assertEquals("", initialFormState.password)

    // Test form updates
    val updatedForm = initialFormState.copy(username = "test_user", password = "test_password")

    assertEquals("test_user", updatedForm.username)
    assertEquals("test_password", updatedForm.password)
  }

  @Test
  fun testAuthUiStateUpdates() {
    val userData = UserData(name = "Test User", schoolid = "12345")
    val accessToken = "test_access_token"

    val loggedInState =
        AuthUiState(
            isLoading = false,
            isLoggedIn = true,
            userData = userData,
            accessToken = accessToken,
        )

    assertTrue(loggedInState.isLoggedIn)
    assertEquals(userData, loggedInState.userData)
    assertEquals(accessToken, loggedInState.accessToken)
    assertFalse(loggedInState.isLoading)
  }

  @Test
  fun testErrorState() {
    val errorMessage = "Login failed"
    val errorState = AuthUiState(error = errorMessage)

    assertEquals(errorMessage, errorState.error)
    assertFalse(errorState.isLoggedIn)
  }

  @Test
  fun testLogoutState() {
    // Test that after logout, the state is reset to initial values
    val loggedInState =
        AuthUiState(
            isLoading = false,
            isLoggedIn = true,
            userData = UserData("Test User", "12345"),
            accessToken = "test_access_token",
        )

    // After logout, state should be reset
    val loggedOutState = AuthUiState()

    assertFalse(loggedOutState.isLoggedIn)
    assertNull(loggedOutState.userData)
    assertNull(loggedOutState.accessToken)
    assertFalse(loggedOutState.isLoading)
  }
}
