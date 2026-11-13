package com.example.myapplication

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.User
import com.example.myapplication.ui_components.screens.AdminProfilesView
import com.example.myapplication.ui_components.screens.MainScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the admin profiles screen.
 *
 * Uses a small navigation graph rather than starting the entire CampsiteApp.
 * Keeps a real Compose state list so deleting a profile recomposes the screen.
 */
@RunWith(AndroidJUnit4::class)
class AdminProfileScreenTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testUser1 = User(
        "entrant", "Test User 1", "testuser1@ualberta.ca", "01234567890", "test1-device-id"
    )
    private val testUser2 = User(
        "campsite owner", "Test User2", "testuser2@ualberta.ca", "9876543210", "test2-device-id"
    )
    private val adminUser = User(
        "admin", "Admin User", "adminUser@ualberta.ca", "0129873456", "admin-device-id"
    )

    private val usersList = mutableStateListOf<User>()

    @Before
    fun setUp() {
        usersList.clear()
        usersList.addAll(listOf(testUser1, testUser2, adminUser))

        composeTestRule.setContent {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = "MainScreen"
            ) {
                composable("MainScreen") {
                    MainScreen(navController, adminUser)
                }
                composable("AdminProfilesView") {
                    AdminProfilesView(
                        navController = navController,
                        users = usersList,
                        onDeleteUser = { selectedUser -> usersList.remove(selectedUser) }
                    )
                }
            }
        }
    }

    private fun openAdminProfiles() {
        composeTestRule.onNodeWithTag("adminProfileBrowse")
            .assertExists()
            .performClick()
        composeTestRule.onNodeWithTag("adminProfilesList")
            .assertExists()
    }

    @Test
    fun adminProfileView_navigation() {
        openAdminProfiles()

        composeTestRule.onNodeWithTag("profilesViewBack")
            .performClick()

        composeTestRule.onNodeWithText("Welcome Admin User!")
            .assertExists()
    }

    @Test
    fun adminProfileView_profileDisplay() {
        openAdminProfiles()

        for (user in listOf(testUser1, testUser2, adminUser)) {
            // LazyColumn items may not be composed until scrolled into view.
            composeTestRule.onNodeWithTag("adminProfilesList")
                .performScrollToNode(hasText("Name: ${user.name}"))

            composeTestRule.onNodeWithText("Name: ${user.name}").assertExists()
            composeTestRule.onNodeWithText("Type: ${user.userType}").assertExists()
            composeTestRule.onNodeWithText("Email: ${user.email}").assertExists()
            composeTestRule.onNodeWithText("Phone: ${user.phoneNumber}").assertExists()
        }
    }

    @Test
    fun adminProfileView_deleteUser_displayUpdate() {
        openAdminProfiles()

        composeTestRule.onNodeWithTag("adminProfilesList")
            .performScrollToNode(hasText("Name: ${testUser1.name}"))

        composeTestRule.onNodeWithTag("test1-device-id delete")
            .performClick()
        composeTestRule.onNodeWithTag("confirmUserDelete")
            .performClick()

        composeTestRule.runOnIdle {
            assertFalse(usersList.any { it.deviceID == testUser1.deviceID })
        }
        composeTestRule.onNodeWithText("Name: ${testUser1.name}")
            .assertDoesNotExist()
    }

    @Test
    fun adminProfileView_cancelDeleteUser_displayUpdate() {
        openAdminProfiles()

        composeTestRule.onNodeWithTag("adminProfilesList")
            .performScrollToNode(hasText("Name: ${testUser1.name}"))

        composeTestRule.onNodeWithTag("test1-device-id delete")
            .performClick()
        composeTestRule.onNodeWithTag("cancelUserDelete")
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(usersList.any { it.deviceID == testUser1.deviceID })
        }
        composeTestRule.onNodeWithTag("test1-device-id delete")
            .assertExists()
    }
}
