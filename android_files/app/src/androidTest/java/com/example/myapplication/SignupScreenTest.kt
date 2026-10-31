package com.example.myapplication

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.AuthState
import com.example.myapplication.features.User
import com.example.myapplication.ui_components.CampsiteApp
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


/**
 * Tests sign up screen correctly handles valid and invalid sign up input.
 *
 * Verifies empty fields blocks account creation and proper input fields allows the user to
 * create an account. Uses a test double Campsite App instance.
 */
@RunWith(AndroidJUnit4::class)
class SignupScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val authFlow = MutableStateFlow<AuthState>(AuthState.NotRegistered)

    private var currentUser by mutableStateOf<User?>(null)


    /**
     * Test double Campsite App instance set up. Runs before every test function.
     */
    @Before
    fun setUp() {
        composeTestRule.setContent {
            CampsiteApp(
                user = currentUser,
                onUpdateUser = { _, _ -> },
                authState = authFlow,
                onAddUser = { newUser -> currentUser = newUser},
                onDeleteUser = { _ -> },
                findUser = { _ -> User() },
                getAndroidId = { _ -> "" },
            )
        }
    }

    /**
     * Test function when the user doesn't provide complete input to create a new profile.
     *
     * The user enters no information and tries to create a profile. The test verifies that the
     * user isn't redirected to the Main Screen and an error message is displayed.
     */
    @Test
    fun signup_emptyFields_showsErrorMessage() {
        composeTestRule
            .onNodeWithTag("welcomeSignUpButton")
            .performClick()

        composeTestRule
            .onNodeWithTag("createAccountButton")
            .performClick()

        composeTestRule
            .onNodeWithText("Welcome Test User!")
            .assertDoesNotExist()

        composeTestRule
            .onNodeWithText("Complete all fields to sign up")
            .assertExists()
    }


    /**
     * Test function when the users provides complete input to create a new profile.
     *
     * Test inputs Owner, Test User, testuser@ualberta.ca, 1234567890 and clicks Create Account
     * button. Verifies that the app redirects the user to the Main Screen and displays Test User
     * as the user's Name.
     */
    @Test
    fun signup_allFieldsFilled_navigatesToMainScreen() {

        composeTestRule
            .onNodeWithTag("welcomeSignUpButton")
            .performClick()

        composeTestRule
            .onNodeWithTag("campsiteOwnerButton")
            .performClick()

        composeTestRule
            .onNodeWithTag("nameTextField")
            .performTextInput("Test User")

        composeTestRule
            .onNodeWithTag("emailTextField")
            .performTextInput("testuser@ualberta.ca")

        composeTestRule
            .onNodeWithTag("phoneNumberTextField")
            .performTextInput("1234567890")

        composeTestRule
            .onNodeWithTag("createAccountButton")
            .performClick()

        composeTestRule
            .onNodeWithText("Welcome Test User!")
            .assertExists()
    }
}