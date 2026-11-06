package com.example.myapplication

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.Campsite
import com.example.myapplication.features.CampsiteLocation
import com.example.myapplication.ui_components.screens.CampsiteDetailsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Test class to verify the campsite details screen.
 *
 * Tests that every detail of a campsite is shown, that optional details (tags and rules) are only
 * shown when filled in, that the rating and lottery status read correctly, that a photo is shown
 * when there is one, and that a missing campsite shows an error message.
 */
@RunWith(AndroidJUnit4::class)
class CampsiteDetailsTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun makeCampsite(
        photo: String = "",
        tags: String = "hiking, lake",
        rating: Double = 0.0,
        termsAndConditions: String = "",
        inLottery: Boolean = false
    ): Campsite {
        val campsite = Campsite(
            name = "Pine Ridge",
            location = CampsiteLocation("Banff Avenue, Banff, AB"),
            description = "Fire pit and bear box",
            occupancy = 6,
            photo = photo,
            tags = tags,
            ownerID = "owner-device-id",
            rating = rating,
            termsAndConditions = termsAndConditions
        )
        campsite.inLottery = inLottery

        return campsite
    }

    private fun showScreen(campsite: Campsite?) {
        composeTestRule.setContent {
            CampsiteDetailsScreen(
                navController = rememberNavController(),
                campsiteName = "Pine Ridge",
                findCampsite = { campsite }
            )
        }
    }

    private fun makePhoto(): String {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)

        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Verifies that the name, address, description, occupancy and tags are shown.
     */
    @Test
    fun campsiteDetailsAreShown_Test() {
        showScreen(makeCampsite())

        composeTestRule.onNodeWithTag("campsiteDetailsName").assertTextEquals("Pine Ridge")
        composeTestRule.onNodeWithTag("campsiteDetailsAddress").assertTextEquals("Banff Avenue, Banff, AB")
        composeTestRule.onNodeWithTag("campsiteDetailsDescription").assertTextEquals("Fire pit and bear box")
        composeTestRule.onNodeWithTag("campsiteDetailsOccupancy").assertTextEquals("6")
        composeTestRule.onNodeWithTag("campsiteDetailsTags").assertTextEquals("hiking, lake")
    }

    /**
     * Verifies that a campsite with no rating says there are no ratings yet.
     */
    @Test
    fun noRating_Test() {
        showScreen(makeCampsite(rating = 0.0))

        composeTestRule.onNodeWithTag("campsiteDetailsRating").assertTextEquals("No ratings yet")
    }

    /**
     * Verifies that a rating is shown with one decimal place out of 5.
     */
    @Test
    fun rating_Test() {
        showScreen(makeCampsite(rating = 4.5))

        composeTestRule
            .onNodeWithTag("campsiteDetailsRating")
            .assertTextEquals("%.1f / 5".format(4.5))
    }

    /**
     * Verifies that blank tags and blank rules are left out of the screen.
     */
    @Test
    fun blankOptionalDetailsAreHidden_Test() {
        showScreen(makeCampsite(tags = "", termsAndConditions = ""))

        composeTestRule.onNodeWithTag("campsiteDetailsTags").assertDoesNotExist()
        composeTestRule.onNodeWithTag("campsiteDetailsRules").assertDoesNotExist()
    }

    /**
     * Verifies that rules and policies are shown when the owner has set them.
     */
    @Test
    fun rulesAreShown_Test() {
        showScreen(makeCampsite(termsAndConditions = "No fires after 10 pm"))

        composeTestRule
            .onNodeWithTag("campsiteDetailsRules")
            .assertTextEquals("No fires after 10 pm")
    }

    /**
     * Verifies that the lottery line says the campsite is in a lottery when it is.
     */
    @Test
    fun inLottery_Test() {
        showScreen(makeCampsite(inLottery = true))

        composeTestRule
            .onNodeWithTag("campsiteDetailsLottery")
            .assertTextEquals("This campsite is in a lottery")
    }

    /**
     * Verifies that the lottery line says the campsite is not in a lottery when it is not.
     */
    @Test
    fun notInLottery_Test() {
        showScreen(makeCampsite(inLottery = false))

        composeTestRule
            .onNodeWithTag("campsiteDetailsLottery")
            .assertTextEquals("This campsite is not in a lottery right now")
    }

    /**
     * Verifies that a campsite without a photo shows "No photo".
     */
    @Test
    fun noPhoto_Test() {
        showScreen(makeCampsite(photo = ""))

        composeTestRule.onNodeWithTag("campsiteDetailsNoPhoto").assertExists()
        composeTestRule.onNodeWithTag("campsiteDetailsPhoto").assertDoesNotExist()
    }

    /**
     * Verifies that a campsite with a valid photo shows the photo.
     */
    @Test
    fun photo_Test() {
        showScreen(makeCampsite(photo = makePhoto()))

        composeTestRule.onNodeWithTag("campsiteDetailsPhoto").assertExists()
        composeTestRule.onNodeWithTag("campsiteDetailsNoPhoto").assertDoesNotExist()
    }

    /**
     * Verifies that a campsite that cannot be found shows an error message and no details.
     */
    @Test
    fun campsiteNotFound_Test() {
        showScreen(null)

        composeTestRule.onNodeWithTag("campsiteNotFound").assertExists()
        composeTestRule.onNodeWithTag("campsiteDetailsName").assertDoesNotExist()
    }
}