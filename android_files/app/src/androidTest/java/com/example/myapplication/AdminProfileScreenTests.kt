package com.example.myapplication

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.AuthState
import com.example.myapplication.features.User
import com.example.myapplication.ui_components.CampsiteApp
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * Test class for AdminProfilesView. Tests that navigation and display work as intended.
 *
 * Tests navigation to and from main screen and admin profiles view and that listed users are
 * displayed correctly.
 */
@RunWith(AndroidJUnit4::class)
class AdminProfileScreenTests {

    @get:Rule
    val composeTestRule = createComposeRule()
    private val testUser1 = User("entrant", "Test User 1", "testuser1@ualberta.ca", "01234567890", "test1-device-id")
    private val testUser2 = User("campsite owner", "Test User2", "testuser2@ualberta.ca", "9876543210", "test2-device-id")
    private val adminUser = User("admin", "Admin User", "adminUser@ualberta.ca", "0129873456", "admin-device-id")
    private val authFlow = MutableStateFlow<AuthState>(AuthState.LoggedIn(adminUser))
    private var currentUser by mutableStateOf<User?>(adminUser)
    private val usersList = mutableListOf(testUser1, testUser2, adminUser)

    /**
     * Test double Campsite App instance set up. Runs before every test function.
     */
    @Before
    fun setUp() {
        composeTestRule.setContent {
            CampsiteApp(
                user = currentUser,
                users = usersList,
                onUpdateUser = { _, _ -> },
                authState = authFlow,
                onAddUser = { },
                onDeleteUser = { _ -> },
                findUser = { _ -> User() },
                getAndroidId = { _ -> "" },
            )
        }
    }


    /**
     * Verifies navigation between main screen and admin profiles view works as intended.
     */
    @Test
    fun adminProfileView_navigation(){
        composeTestRule
            .onNodeWithTag("adminProfileBrowse")
            .performClick()

        composeTestRule
            .onNodeWithTag("adminProfilesList")
            .assertExists()

        composeTestRule
            .onNodeWithTag("profilesViewBack")
            .performClick()

        composeTestRule
            .onNodeWithText("Welcome Admin User!")
            .assertExists()
    }

    /**
     * Verifies all fields, expect deviceID, of all users are displayed as intended.
     */
    @Test
    fun adminProfileView_profileDisplay(){
        composeTestRule
            .onNodeWithTag("adminProfileBrowse")
            .performClick()

        for (user in usersList) {
            composeTestRule
                .onNodeWithText("Type: ${user.userType}")
                .assertExists()

            composeTestRule
                .onNodeWithText("Name: ${user.name}")
                .assertExists()

            composeTestRule
                .onNodeWithText("Email: ${user.email}")
                .assertExists()

            composeTestRule
                .onNodeWithText("Phone: ${user.phoneNumber}")
                .assertExists()
        }
    }
}