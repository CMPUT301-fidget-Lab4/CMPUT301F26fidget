package com.example.myapplication

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.Campsite
import com.example.myapplication.features.User
import com.example.myapplication.ui_components.screens.RegisterCampsiteScreen
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class to verify the register campsite screen.
 *
 * Tests that only campsite owners can see the form and that the form rejects empty fields,
 * a bad occupancy, a name with a "/" and a name that already exists, without saving anything.
 *
 * A successful registration is not tested here because it needs the geocoder to find a real
 * address, which is unreliable on emulators.
 */
@RunWith(AndroidJUnit4::class)
class RegisterCampsiteTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val owner = User("campsite owner", "Test Owner", "owner@ualberta.ca", "1234567890", "owner-device-id")

    private val entrant = User("entrant", "Test Entrant", "entrant@ualberta.ca", "0987654321", "entrant-device-id")

    private val existingCampsite = Campsite(name = "Pine Ridge", ownerID = "other-device-id")

    private var addedCampsite: Campsite? = null

    @Before
    fun resetAddedCampsite() {
        addedCampsite = null
    }

    private fun showScreen(user: User?) {
        composeTestRule.setContent {
            RegisterCampsiteScreen(
                navController = rememberNavController(),
                userProfile = user,
                onAddCampsite = { addedCampsite = it },
                findCampsite = { name -> if (name == existingCampsite.name) existingCampsite else null }
            )
        }
    }

    private fun enterText(tag: String, text: String) {
        composeTestRule
            .onNodeWithTag(tag)
            .performScrollTo()
            .performTextInput(text)
    }

    private fun fillForm(name: String, address: String, description: String, occupancy: String) {
        enterText("registerCampsiteName", name)
        enterText("registerCampsiteAddress", address)
        enterText("registerCampsiteDescription", description)
        enterText("registerCampsiteOccupancy", occupancy)
    }

    private fun pressRegister() {
        composeTestRule
            .onNodeWithTag("registerCampsiteButton")
            .performScrollTo()
            .performClick()
    }

    private fun assertError(message: String) {
        composeTestRule
            .onNodeWithTag("registerCampsiteError")
            .assertTextEquals(message)

        assertNull(addedCampsite)
    }

    /**
     * Verifies that an entrant sees an error message instead of the registration form.
     */
    @Test
    fun entrantCannotRegister_Test() {
        showScreen(entrant)

        composeTestRule
            .onNodeWithText("Only campsite owners can register a campsite")
            .assertExists()

        composeTestRule
            .onNodeWithTag("registerCampsiteName")
            .assertDoesNotExist()
    }

    /**
     * Verifies that a campsite owner sees the registration form.
     */
    @Test
    fun ownerSeesForm_Test() {
        showScreen(owner)

        composeTestRule
            .onNodeWithTag("registerCampsiteName")
            .assertExists()

        composeTestRule
            .onNodeWithTag("registerCampsiteButton")
            .assertExists()
    }

    /**
     * Verifies that pressing Register with empty fields shows an error and saves nothing.
     */
    @Test
    fun emptyFields_Test() {
        showScreen(owner)

        pressRegister()

        assertError("Fill in the name, address and description")
    }

    /**
     * Verifies that a non-numeric occupancy is rejected.
     */
    @Test
    fun occupancyNotANumber_Test() {
        showScreen(owner)

        fillForm("New Camp", "Banff Avenue, Banff, AB", "Fire pit", "abc")
        pressRegister()

        assertError("Maximum occupancy must be a positive number")
    }

    /**
     * Verifies that an occupancy of zero is rejected.
     */
    @Test
    fun occupancyZero_Test() {
        showScreen(owner)

        fillForm("New Camp", "Banff Avenue, Banff, AB", "Fire pit", "0")
        pressRegister()

        assertError("Maximum occupancy must be a positive number")
    }

    /**
     * Verifies that a name containing a "/" is rejected, since it is used as the Firestore
     * document ID.
     */
    @Test
    fun nameWithSlash_Test() {
        showScreen(owner)

        fillForm("Pine/Ridge", "Banff Avenue, Banff, AB", "Fire pit", "6")
        pressRegister()

        assertError("The name cannot contain a /")
    }

    /**
     * Verifies that a name already used by another campsite is rejected.
     */
    @Test
    fun duplicateName_Test() {
        showScreen(owner)

        fillForm("Pine Ridge", "Banff Avenue, Banff, AB", "Fire pit", "6")
        pressRegister()

        assertError("A campsite with that name already exists")
    }
}