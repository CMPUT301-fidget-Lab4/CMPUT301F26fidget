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
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before

@RunWith(AndroidJUnit4::class)
class ProfileScreenTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testUser = User("entrant", "Test User", "testuser@ualberta.ca", "1234567890", "test-device-id")

    private val authFlow = MutableStateFlow(AuthState.LoggedIn(testUser))

    private var currentUser by mutableStateOf<User?>(testUser)

    @Before
    fun setUp() {
        composeTestRule.setContent {
            CampsiteApp(
                user = currentUser,
                onUpdateUser = { newUser, _ -> currentUser = newUser },
                authState = authFlow,
                onAddUser = { },
                onDeleteUser = { },
                findUser = { _ -> currentUser },
                getAndroidId = { _ -> currentUser!!.deviceID },
            )
        }
    }
    

    @Test
    fun mainScreen_profileButton_Navigation() {
        composeTestRule
            .onNodeWithTag("profileButton")
            .performClick()

        composeTestRule
            .onNodeWithText("Update Profile")
            .assertExists()
    }

    @Test
    fun profile_backButton_Navigation() {
        composeTestRule
            .onNodeWithTag("profileButton")
            .performClick()

        composeTestRule
            .onNodeWithText("Update Profile")
            .assertExists()

        composeTestRule
            .onNodeWithTag("returnToMainScreen")
            .performClick()

        composeTestRule
            .onNodeWithText("Welcome ${testUser.name}!")
            .assertExists()
    }

    @Test
    fun profile_initialLoad_displayCorrectUserInformation() {
        composeTestRule
            .onNodeWithTag("profileButton")
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

    @Test
    fun profile_initialLoad_ReadOnly() {
        composeTestRule
            .onNodeWithTag("profileButton")
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

    @Test
    fun profile_editing_isSaved() {
        composeTestRule
            .onNodeWithTag("profileButton")
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
            .onNodeWithTag("saveProfile")
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

    @Test
    fun profile_editing_cancelChanges() {
        composeTestRule
            .onNodeWithTag("profileButton")
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
            .onNodeWithTag("cancelChanges")
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
}