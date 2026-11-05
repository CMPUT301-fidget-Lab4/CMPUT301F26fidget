package com.example.myapplication

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.User
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.myapplication.features.AuthState
import com.example.myapplication.ui_components.CampsiteApp
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before

/**
 * Test for the profile screen and corresponding buttons and actions.
 *
 * Test navigation between the profile and main screen, entering data before editing is allowed,
 * entering and submitting profile updates, cancelling updates, and deleting and canceling user
 * profile delete requests. Uses a test double CampsiteApp instance.
 */

@RunWith(AndroidJUnit4::class)
class ProfileScreenTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testUser = User("entrant", "Test User", "testuser@ualberta.ca", "1234567890", "test-device-id")

    private val authFlow = MutableStateFlow<AuthState>(AuthState.LoggedIn(testUser))

    private var currentUser by mutableStateOf<User?>(testUser)

    /**
     * Test double Campsite App instance set up. Runs before every test function.
     */
    @Before
    fun setUp() {
        composeTestRule.setContent {
            CampsiteApp(
                user = currentUser,
                onUpdateUser = { newUser, _ -> currentUser = newUser },
                authState = authFlow,
                onAddUser = { },
                onDeleteUser = { _ ->
                    currentUser = null
                    authFlow.value = AuthState.NotRegistered},
                findUser = { _ -> currentUser },
                getAndroidId = { _ -> currentUser!!.deviceID },
            )
        }
    }

    /**
     * Verifies navigation to profile screen works as intended.
     */
    @Test
    fun mainScreen_profileButton_Navigation() {
        composeTestRule
            .onNodeWithTag("ProfileScreen")
            .performClick()

        composeTestRule
            .onNodeWithText("User Profile")
            .assertExists()
    }

    /**
     * Verifies navigation to profile screen and back to main screen works as intended.
     */
    @Test
    fun profile_backButton_Navigation() {
        composeTestRule
            .onNodeWithTag("ProfileScreen")
            .performClick()

        composeTestRule
            .onNodeWithText("User Profile")
            .assertExists()

        composeTestRule
            .onNodeWithTag("returnMainScreen")
            .performClick()

        composeTestRule
            .onNodeWithText("Welcome ${testUser.name}!")
            .assertExists()
    }

    /**
     * Verifies profile screen displays correct user information.
     */
    @Test
    fun profile_initialLoad_displayCorrectUserInformation() {
        composeTestRule
            .onNodeWithTag("ProfileScreen")
            .performClick()

        composeTestRule
            .onNodeWithText(testUser.userType)
            .assertExists()

        composeTestRule
            .onNodeWithText(testUser.name)
            .assertExists()

        composeTestRule
            .onNodeWithText(testUser.phoneNumber)
            .assertExists()

        composeTestRule
            .onNodeWithText(testUser.email)
            .assertExists()
    }

    /**
     * Verifies profile screen initially loads profile information as read-only.
     */
    @Test
    fun profile_initialLoad_ReadOnly() {
        composeTestRule
            .onNodeWithTag("ProfileScreen")
            .performClick()

        composeTestRule
            .onNodeWithTag("profileUserType")
            .assert(hasSetTextAction().not())

        composeTestRule
            .onNodeWithTag("profileName")
            .assert(hasSetTextAction().not())

        composeTestRule
            .onNodeWithTag("profilePhoneNumber")
            .assert(hasSetTextAction().not())

        composeTestRule
            .onNodeWithTag("profileEmail")
            .assert(hasSetTextAction().not())
    }

    /**
     * Verifies edited user profile is saved by test double Campsite Apps methods and properly
     * displayed after being saved.
     */
    @Test
    fun profile_editing_isSaved() {
        composeTestRule
            .onNodeWithTag("ProfileScreen")
            .performClick()

        composeTestRule
            .onNodeWithTag("editProfile")
            .performClick()

        composeTestRule
            .onNodeWithTag("profileName")
            .performTextReplacement("Super User")

        composeTestRule
            .onNodeWithTag("profilePhoneNumber")
            .performTextReplacement("9876543210")

        composeTestRule
            .onNodeWithTag("profileEmail")
            .performTextReplacement("superuser@ualberta.ca")

        composeTestRule
            .onNodeWithTag("saveProfileEdits")
            .performClick()

        val inputUser = User(testUser.userType, "Super User", "superuser@ualberta.ca", "9876543210", testUser.deviceID)

        assertEquals(inputUser, currentUser)

        composeTestRule
            .onNodeWithText(currentUser!!.name)
            .assertExists()

        composeTestRule
            .onNodeWithText(currentUser!!.phoneNumber)
            .assertExists()

        composeTestRule
            .onNodeWithText(currentUser!!.email)
            .assertExists()
    }

    /**
     * Verifies canceled user profile edits aren't saved by test double campsite app methods
     * or displayed on the profile screen.
     */
    @Test
    fun profile_editing_cancelChanges() {
        composeTestRule
            .onNodeWithTag("ProfileScreen")
            .performClick()

        composeTestRule
            .onNodeWithTag("editProfile")
            .performClick()

        composeTestRule
            .onNodeWithTag("profileName")
            .performTextReplacement("Super User")

        composeTestRule
            .onNodeWithTag("profilePhoneNumber")
            .performTextReplacement("9876543210")

        composeTestRule
            .onNodeWithTag("profileEmail")
            .performTextReplacement("superuser@ualberta.ca")

        composeTestRule
            .onNodeWithTag("cancelProfileChanges")
            .performClick()

        assertEquals(testUser, currentUser)

        composeTestRule
            .onNodeWithText(currentUser!!.name)
            .assertExists()

        composeTestRule
            .onNodeWithText(currentUser!!.phoneNumber)
            .assertExists()

        composeTestRule
            .onNodeWithText(currentUser!!.email)
            .assertExists()
    }

    /**
     * Verifies after a user deletes their profile that they are redirected to the home screen
     * and are treated as a new user.
     */
    @Test
    fun profile_delete_confirm() {
        composeTestRule
            .onNodeWithTag("ProfileScreen")
            .performClick()

        composeTestRule
            .onNodeWithTag("deleteProfile")
            .performClick()

        composeTestRule
            .onNodeWithTag("confirmDeleteProfile")
            .performClick()

        composeTestRule
            .onNodeWithTag("welcomeSignUpButton")
            .assertExists()

        assertNull(currentUser)
    }

    /**
     * Verifies canceling a delete profile request returns you to the profile screen.
     */
    @Test
    fun profile_delete_cancel() {
        composeTestRule
            .onNodeWithTag("ProfileScreen")
            .performClick()

        composeTestRule
            .onNodeWithTag("deleteProfile")
            .performClick()

        composeTestRule
            .onNodeWithTag("cancelDeleteProfile")
            .performClick()

        composeTestRule
            .onNodeWithTag("cancelDeleteProfile")
            .assertDoesNotExist()

        assert(currentUser == testUser)
    }


}