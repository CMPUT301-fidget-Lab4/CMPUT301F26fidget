package com.example.myapplication

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.myapplication.features.User
import com.example.myapplication.ui_components.CampsiteApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.runner.RunWith


/**
 * Test class to verify users are redirected as intended from the home screen.
 *
 * Tests that a new user is prompted to the sign-up screen with a functional button to perform
 * the navigation and that a returning user is automatically redirected to the main screen.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val authFlow = MutableStateFlow<AuthState>(AuthState.NotRegistered)

    private val testUser = User("entrant", "Test User", "testuser@ualberta.ca", "1234567890", "test-device-id")

    private var currentUser by mutableStateOf<User?>(null)

    @Before
    fun setUp() {
        composeTestRule.setContent {
            CampsiteApp(
                user = currentUser,
                onUpdateUser = { _, _ -> },
                authState = authFlow,
                onAddUser = { _ -> },
                onDeleteUser = { _ -> },
                findUser = { _ -> User() },
                getAndroidId = { _ -> "" },
            )
        }
    }


    /**
     * Verifies that a new user is prompted to sign up with a button to navigate
     * to the sign-up screen.
     */
    @Test
    fun newUserRedirect_Test() {
        composeTestRule
            .onNodeWithText("Device Account Not Found. Continue To Sign In")
            .assertExists()

        composeTestRule
            .onNodeWithTag("welcomeSignUpButton")
            .performClick()

        composeTestRule
            .onNodeWithText("Sign Up")
            .assertExists()
    }

    /**
     * Verifies that a returning user is automatically redirected to the main screen and
     * [User.name] is displayed.
     */
    @Test
    fun returningUser_Test() {
        composeTestRule.runOnIdle {
            currentUser = testUser
            authFlow.value = AuthState.LoggedIn(testUser)
        }

        composeTestRule
            .onNodeWithText("Welcome ${testUser.name}!")
            .assertExists()
    }
}