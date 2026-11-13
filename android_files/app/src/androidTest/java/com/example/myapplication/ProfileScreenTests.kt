package com.example.myapplication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.AuthState
import com.example.myapplication.features.User
import com.example.myapplication.ui_components.screens.HomeScreen
import com.example.myapplication.ui_components.screens.MainScreen
import com.example.myapplication.ui_components.screens.ProfileScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for profile navigation, display, edits, and deletion.
 *
 * Uses the real screens and test callbacks. No Firebase or MockK/Mockito
 * repositories are needed for these UI behaviors.
 */
@RunWith(AndroidJUnit4::class)
class ProfileScreenTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testUser = User(
        userType = "entrant",
        name = "Test User",
        email = "testuser@ualberta.ca",
        phoneNumber = "1234567890",
        deviceID = "test-device-id"
    )

    private val authFlow = MutableStateFlow<AuthState>(AuthState.LoggedIn(testUser))
    private var currentUser by mutableStateOf<User?>(testUser)

    @Before
    fun setUp() {
        currentUser = testUser
        authFlow.value = AuthState.LoggedIn(testUser)

        composeTestRule.setContent {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "MainScreen"
            ) {
                composable("MainScreen") {
                    MainScreen(
                        navController = navController,
                        userProfile = currentUser
                    )
                }

                composable("ProfileScreen") {
                    ProfileScreen(
                        navController = navController,
                        userProfile = currentUser,
                        onUpdateUser = { updatedUser, _ ->
                            currentUser = updatedUser
                            authFlow.value = AuthState.LoggedIn(updatedUser)
                        },
                        onDeleteUser = { _ ->
                            currentUser = null
                            authFlow.value = AuthState.NotRegistered
                        }
                    )
                }

                composable("HomeScreen") {
                    HomeScreen(
                        navController = navController,
                        authState = authFlow
                    )
                }
            }
        }
    }

    private fun openProfile() {
        composeTestRule
            .onNodeWithTag("ProfileScreen")
            .performClick()

        composeTestRule
            .onNodeWithText("User Profile")
            .assertExists()
    }

    /** MainScreen can open the profile screen. */
    @Test
    fun mainScreen_profileButton_Navigation() {
        openProfile()
    }

    /** Profile's back button returns to MainScreen. */
    @Test
    fun profile_backButton_Navigation() {
        openProfile()

        composeTestRule
            .onNodeWithTag("returnMainScreen")
            .performClick()

        composeTestRule
            .onNodeWithText("Welcome ${testUser.name}!")
            .assertExists()
    }

    /** The profile initially shows all four user fields. */
    @Test
    fun profile_initialLoad_displayCorrectUserInformation() {
        openProfile()

        composeTestRule.onNodeWithText(testUser.userType).assertExists()
        composeTestRule.onNodeWithText(testUser.name).assertExists()
        composeTestRule.onNodeWithText(testUser.phoneNumber).assertExists()
        composeTestRule.onNodeWithText(testUser.email).assertExists()
    }

    /** The profile fields cannot be edited until Edit Profile is pressed. */
    @Test
    fun profile_initialLoad_ReadOnly() {
        openProfile()

        composeTestRule.onNodeWithTag("profileUserType")
            .assert(hasSetTextAction().not())
        composeTestRule.onNodeWithTag("profileName")
            .assert(hasSetTextAction().not())
        composeTestRule.onNodeWithTag("profilePhoneNumber")
            .assert(hasSetTextAction().not())
        composeTestRule.onNodeWithTag("profileEmail")
            .assert(hasSetTextAction().not())
    }

    /** Saving edits updates the current user and the displayed profile. */
    @Test
    fun profile_editing_isSaved() {
        openProfile()

        composeTestRule.onNodeWithTag("editProfile").performClick()
        composeTestRule.onNodeWithTag("profileName")
            .performTextReplacement("Super User")
        composeTestRule.onNodeWithTag("profilePhoneNumber")
            .performTextReplacement("9876543210")
        composeTestRule.onNodeWithTag("profileEmail")
            .performTextReplacement("superuser@ualberta.ca")
        composeTestRule.onNodeWithTag("saveProfileEdits").performClick()

        composeTestRule.runOnIdle {
            assertEquals("Super User", currentUser?.name)
            assertEquals("9876543210", currentUser?.phoneNumber)
            assertEquals("superuser@ualberta.ca", currentUser?.email)
            assertEquals(testUser.userType, currentUser?.userType)
            assertEquals(testUser.deviceID, currentUser?.deviceID)
        }

        composeTestRule.onNodeWithText("Super User").assertExists()
        composeTestRule.onNodeWithText("9876543210").assertExists()
        composeTestRule.onNodeWithText("superuser@ualberta.ca").assertExists()
    }

    /** Cancelling edits restores original values without invoking the update callback. */
    @Test
    fun profile_editing_cancelChanges() {
        openProfile()

        composeTestRule.onNodeWithTag("editProfile").performClick()
        composeTestRule.onNodeWithTag("profileName")
            .performTextReplacement("Super User")
        composeTestRule.onNodeWithTag("profilePhoneNumber")
            .performTextReplacement("9876543210")
        composeTestRule.onNodeWithTag("profileEmail")
            .performTextReplacement("superuser@ualberta.ca")
        composeTestRule.onNodeWithTag("cancelProfileChanges").performClick()

        composeTestRule.runOnIdle {
            assertEquals("Test User", currentUser?.name)
            assertEquals("1234567890", currentUser?.phoneNumber)
            assertEquals("testuser@ualberta.ca", currentUser?.email)
        }

        composeTestRule.onNodeWithText("Test User").assertExists()
        composeTestRule.onNodeWithText("1234567890").assertExists()
        composeTestRule.onNodeWithText("testuser@ualberta.ca").assertExists()
    }

    /** Confirming deletion clears the current user and opens HomeScreen. */
    @Test
    fun profile_delete_confirm() {
        openProfile()

        composeTestRule.onNodeWithTag("deleteProfile").performClick()
        composeTestRule.onNodeWithTag("confirmDeleteProfile").performClick()

        composeTestRule.runOnIdle {
            assertNull(currentUser)
        }
        composeTestRule.onNodeWithTag("welcomeSignUpButton").assertExists()
    }

    /** Cancelling deletion closes the dialog without deleting the user. */
    @Test
    fun profile_delete_cancel() {
        openProfile()

        composeTestRule.onNodeWithTag("deleteProfile").performClick()
        composeTestRule.onNodeWithTag("cancelDeleteProfile").performClick()

        composeTestRule.onNodeWithTag("cancelDeleteProfile").assertDoesNotExist()
        composeTestRule.runOnIdle {
            assertEquals(testUser, currentUser)
        }
        composeTestRule.onNodeWithText("User Profile").assertExists()
    }
}
