package com.example.myapplication
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString

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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for account signup.
 *
 * Verifies:
 * 1. Empty fields prevent account creation.
 * 2. Valid information creates a user.
 * 3. Successful signup navigates to MainScreen.
 *
 * Uses in-memory state instead of Firebase.
 * No Mockito, MockK, or Espresso keyboard interaction.
 */
@RunWith(AndroidJUnit4::class)
class SignupScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var createdUser by mutableStateOf<User?>(null)

    private val testDeviceID = "test-device-id"

    /**
     * Creates a small navigation graph containing
     * only the screens needed for signup testing.
     */
    @Before
    fun setUp() {

        createdUser = null

        val authFlow = MutableStateFlow<AuthState>(
            AuthState.NotRegistered
        )

        composeTestRule.setContent {

            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "HomeScreen"
            ) {

                composable("HomeScreen") {
                    HomeScreen(
                        navController = navController,
                        authState = authFlow
                    )
                }

                composable("Signup") {
                    Signup(
                        navController = navController,

                        onAddUser = { newUser ->
                            createdUser = newUser
                        },

                        getAndroidId = { _ ->
                            testDeviceID
                        }
                    )
                }

                composable("MainScreen") {
                    MainScreen(
                        navController = navController,
                        userProfile = createdUser
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
    }

    /**
     * Opens Signup from HomeScreen.
     */
    private fun goToSignup() {

        composeTestRule
            .onNodeWithTag("welcomeSignUpButton")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Sign Up")
            .assertExists()
    }

    /**
     * Sets a text field's value using Compose semantics.
     *
     * This avoids opening the Android software keyboard
     * during the test.
     */
    private fun enterText(
        tag: String,
        value: String
    ) {
        composeTestRule
            .onNodeWithTag(tag)
            .performSemanticsAction(
                SemanticsActions.SetText
            ) { setText ->
                setText(AnnotatedString(value))
            }
    }

    /**
     * Submits the signup form.
     */
    private fun submitSignup() {

        composeTestRule
            .onNodeWithTag("createAccountButton")
            .performClick()

        composeTestRule.waitForIdle()
    }

    /**
     * TEST 1:
     *
     * Verifies that submitting the signup form
     * with empty fields displays an error and
     * does not create a user.
     */
    @Test
    fun signup_emptyFields_showsErrorMessage() {

        goToSignup()

        submitSignup()

        composeTestRule
            .onNodeWithText(
                "Complete all fields to sign up"
            )
            .assertExists()

        composeTestRule
            .onNodeWithText("Welcome Test User!")
            .assertDoesNotExist()

        composeTestRule.runOnIdle {
            assertNull(
                "A user must not be created with empty fields",
                createdUser
            )
        }
    }

    /**
     * TEST 2:
     *
     * Verifies that a campsite owner can:
     * - Select a user type.
     * - Enter valid account details.
     * - Submit the signup form.
     * - Navigate to MainScreen.
     *
     * Also verifies the generated User object.
     */
    @Test
    fun signup_allFieldsFilled_navigatesToMainScreen() {

        goToSignup()

        // Select campsite owner
        composeTestRule
            .onNodeWithTag("campsiteOwnerButton")
            .performClick()

        // Enter account information without using
        // the software keyboard.
        enterText(
            "nameTextField",
            "Test User"
        )

        enterText(
            "emailTextField",
            "testuser@ualberta.ca"
        )

        enterText(
            "phoneNumberTextField",
            "1234567890"
        )

        composeTestRule.waitForIdle()

        // Submit signup
        submitSignup()

        // Wait for MainScreen to display the user
        composeTestRule.waitUntil(
            timeoutMillis = 10_000
        ) {
            composeTestRule
                .onAllNodesWithText("Welcome Test User!")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Verify successful navigation
        composeTestRule
            .onNodeWithText("Welcome Test User!")
            .assertExists()

        // Verify that the user was created
        composeTestRule.runOnIdle {

            val user = createdUser

            assertNotNull(
                "Signup should create a User object",
                user
            )

            requireNotNull(user)

            assertEquals(
                "campsite owner",
                user.userType
            )

            assertEquals(
                "Test User",
                user.name
            )

            assertEquals(
                "testuser@ualberta.ca",
                user.email
            )

            assertEquals(
                "1234567890",
                user.phoneNumber
            )

            assertEquals(
                testDeviceID,
                user.deviceID
            )
        }
    }
}
