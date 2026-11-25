package com.example.myapplication.featureModelsTests

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.userModels.User
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReservedDatesTests {


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


}