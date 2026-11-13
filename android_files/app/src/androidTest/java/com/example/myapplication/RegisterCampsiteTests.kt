
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
 * Tests the RegisterCampsiteScreen.
 *
 * Verifies:
 * - Entrants cannot register campsites.
 * - Campsite owners can access the registration form.
 * - Empty required fields are rejected.
 * - Invalid occupancy is rejected.
 * - Invalid campsite names are rejected.
 * - Duplicate campsite names are rejected.
 * - Missing street information is rejected.
 *
 * These tests do not require Firebase or geocoding.
 */
@RunWith(AndroidJUnit4::class)
class RegisterCampsiteTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val owner = User(
        userType = "campsite owner",
        name = "Test Owner",
        email = "owner@ualberta.ca",
        phoneNumber = "1234567890",
        deviceID = "owner-device-id"
    )

    private val entrant = User(
        userType = "entrant",
        name = "Test Entrant",
        email = "entrant@ualberta.ca",
        phoneNumber = "0987654321",
        deviceID = "entrant-device-id"
    )

    private val existingCampsite = Campsite(
        name = "Pine Ridge",
        ownerID = "other-device-id"
    )

    private var addedCampsite: Campsite? = null

    private val requiredFieldsError =
        "Fill in the name, address, city, province and description"

    /**
     * Resets the captured campsite before each test.
     */
    @Before
    fun setUp() {
        addedCampsite = null
    }

    /**
     * Displays the registration screen with the selected user.
     *
     * Uses test callbacks instead of Firebase.
     */
    private fun showScreen(user: User?) {

        composeTestRule.setContent {

            RegisterCampsiteScreen(
                navController = rememberNavController(),
                userProfile = user,

                onAddCampsite = { campsite ->
                    addedCampsite = campsite
                },

                findCampsite = { name ->
                    if (name == existingCampsite.name) {
                        existingCampsite
                    } else {
                        null
                    }
                }
            )
        }

        composeTestRule.waitForIdle()
    }

    /**
     * Scrolls to a field and enters text.
     */
    private fun enterText(
        tag: String,
        text: String
    ) {
        composeTestRule
            .onNodeWithTag(tag)
            .performScrollTo()
            .performTextInput(text)
    }

    /**
     * Populates all required campsite fields.
     */
    private fun fillForm(
        name: String,
        addressNumber: String,
        streetName: String,
        city: String,
        province: String,
        description: String,
        occupancy: String
    ) {

        enterText(
            "registerCampsiteName",
            name
        )

        enterText(
            "registerCampsiteAddressNumber",
            addressNumber
        )

        enterText(
            "registerCampsiteStreetName",
            streetName
        )

        enterText(
            "registerCampsiteCity",
            city
        )

        enterText(
            "registerCampsiteProvince",
            province
        )

        enterText(
            "registerCampsiteDescription",
            description
        )

        enterText(
            "registerCampsiteOccupancy",
            occupancy
        )
    }

    /**
     * Clicks the registration button.
     */
    private fun pressRegister() {

        composeTestRule
            .onNodeWithTag("registerCampsiteButton")
            .performScrollTo()
            .performClick()

        composeTestRule.waitForIdle()
    }

    /**
     * Verifies that the expected validation message appears
     * and no campsite was added.
     */
    private fun assertError(message: String) {

        composeTestRule
            .onNodeWithTag("registerCampsiteError")
            .assertExists()
            .assertTextEquals(message)

        assertNull(
            "An invalid campsite should not be saved",
            addedCampsite
        )
    }

    /**
     * Entrants must not be allowed to register campsites.
     */
    @Test
    fun entrantCannotRegister_Test() {

        showScreen(entrant)

        composeTestRule
            .onNodeWithText(
                "Only campsite owners can register a campsite"
            )
            .assertExists()

        composeTestRule
            .onNodeWithTag("registerCampsiteName")
            .assertDoesNotExist()

        assertNull(addedCampsite)
    }

    /**
     * Campsite owners should see the registration form.
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

        composeTestRule
            .onNodeWithTag("registerCampsiteOccupancy")
            .assertExists()
    }

    /**
     * Submitting empty fields should show the required
     * information error.
     */
    @Test
    fun emptyFields_Test() {

        showScreen(owner)

        pressRegister()

        assertError(requiredFieldsError)
    }

    /**
     * Non-numeric occupancy should be rejected.
     */
    @Test
    fun occupancyNotANumber_Test() {

        showScreen(owner)

        fillForm(
            name = "New Camp",
            addressNumber = "100",
            streetName = "Banff Avenue",
            city = "Banff",
            province = "AB",
            description = "Fire pit",
            occupancy = "abc"
        )

        pressRegister()

        assertError(
            "Maximum occupancy must be a positive number"
        )
    }

    /**
     * Zero occupancy should be rejected.
     */
    @Test
    fun occupancyZero_Test() {

        showScreen(owner)

        fillForm(
            name = "New Camp",
            addressNumber = "100",
            streetName = "Banff Avenue",
            city = "Banff",
            province = "AB",
            description = "Fire pit",
            occupancy = "0"
        )

        pressRegister()

        assertError(
            "Maximum occupancy must be a positive number"
        )
    }

    /**
     * Campsite names containing "/" are invalid
     * because names are used as Firestore document IDs.
     */
    @Test
    fun nameWithSlash_Test() {

        showScreen(owner)

        fillForm(
            name = "Pine/Ridge",
            addressNumber = "100",
            streetName = "Banff Avenue",
            city = "Banff",
            province = "AB",
            description = "Fire pit",
            occupancy = "6"
        )

        pressRegister()

        assertError(
            "The name cannot contain a /"
        )
    }

    /**
     * Duplicate campsite names must be rejected.
     */
    @Test
    fun duplicateName_Test() {

        showScreen(owner)

        fillForm(
            name = "Pine Ridge",
            addressNumber = "100",
            streetName = "Banff Avenue",
            city = "Banff",
            province = "AB",
            description = "Fire pit",
            occupancy = "6"
        )

        pressRegister()

        assertError(
            "A campsite with that name already exists"
        )
    }

    /**
     * A missing street name should prevent registration.
     */
    @Test
    fun missingStreetName_Test() {

        showScreen(owner)

        enterText(
            "registerCampsiteName",
            "New Camp"
        )

        enterText(
            "registerCampsiteAddressNumber",
            "100"
        )

        enterText(
            "registerCampsiteDescription",
            "Fire pit"
        )

        enterText(
            "registerCampsiteOccupancy",
            "6"
        )

        pressRegister()

        assertError(requiredFieldsError)
    }
}
