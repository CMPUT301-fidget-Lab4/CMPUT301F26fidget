package com.example.myapplication

import android.annotation.SuppressLint
import android.provider.Settings.Secure
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.features.MainActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignupScreenTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @SuppressLint("HardwareIds")
    private val currentDeviceID: String = Secure.getString(
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
        Secure.ANDROID_ID
    )

    private lateinit var scenario: ActivityScenario<MainActivity>

    // TODO: temporary - remove once tests no longer hit the real database
    private fun deleteCurrentDeviceUser() {
        Tasks.await(Firebase.firestore.collection("users").document(currentDeviceID).delete())
    }

    @Before
    fun setUp() {
        deleteCurrentDeviceUser()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        deleteCurrentDeviceUser()
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