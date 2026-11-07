package com.example.myapplication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.AuthState
import com.example.myapplication.features.User
import com.example.myapplication.ui_components.screens.HomeScreen
import com.example.myapplication.ui_components.screens.MainScreen
import com.example.myapplication.ui_components.screens.Signup
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests HomeScreen navigation without mocking unrelated Firebase repositories. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testUser = User(
        userType = "entrant",
        name = "Test User",
        email = "testuser@ualberta.ca",
        phoneNumber = "1234567890",
        deviceID = "test-device-id"
    )

    private val authFlow = MutableStateFlow<AuthState>(AuthState.NotRegistered)
    private var currentUser by mutableStateOf<User?>(null)

    @Before
    fun setUp() {
        currentUser = null
        authFlow.value = AuthState.NotRegistered

        composeTestRule.setContent {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "HomeScreen"
            ) {
                composable("HomeScreen") {
                    HomeScreen(navController = navController, authState = authFlow)
                }
                composable("Signup") {
                    Signup(
                        navController = navController,
                        onAddUser = { newUser ->
                            currentUser = newUser
                            authFlow.value = AuthState.LoggedIn(newUser)
                        },
                        getAndroidId = { _ -> testUser.deviceID }
                    )
                }
                composable("MainScreen") {
                    MainScreen(navController = navController, userProfile = currentUser)
                }
            }
        }
    }

    /** A new user sees the prompt and can reach the real signup screen. */
    @Test
    fun newUserRedirect_Test() {
        composeTestRule
            .onNodeWithText("Account Not Found. Continue To Sign In")
            .assertExists()

        composeTestRule
            .onNodeWithTag("welcomeSignUpButton")
            .performClick()

        composeTestRule
            .onNodeWithText("Sign Up")
            .assertExists()
    }

    /** Logged-in users are redirected to MainScreen and greeted by name. */
    @Test
    fun returningUser_Test() {
        composeTestRule.runOnIdle {
            currentUser = testUser
            authFlow.value = AuthState.LoggedIn(testUser)
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Welcome ${testUser.name}!")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule
            .onNodeWithText("Welcome ${testUser.name}!")
            .assertExists()
    }
}
