package com.example.myapplication.frontEnd.screens.campsiteViews

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.featureModels.CampsiteLocation
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CampsiteListFilterUiTest {

    @get:Rule val composeRule = createComposeRule()

    private val campsites = mapOf(
        "Alpha" to Campsite(name = "Alpha", location = CampsiteLocation(address = "1 Main St, Edmonton, AB"), occupancy = 10),
        "Beta" to Campsite(name = "Beta", location = CampsiteLocation(address = "2 Lake Rd, Calgary, AB"), occupancy = 4)
    )

    @Before fun setUp() {
        composeRule.setContent {
            CampsiteListScreen(rememberNavController(), null, campsites, ownedOnly = false)
        }
    }

    @Test fun showsAllCampsitesByDefault() {
        composeRule.onNodeWithTag("campsiteRow_Alpha").assertExists()
        composeRule.onNodeWithTag("campsiteRow_Beta").assertExists()
        composeRule.onNodeWithText("Displaying 2 campsites").assertExists()
    }

    @Test fun typingDoesNotFilterUntilButtonPressed() {
        composeRule.onNodeWithTag("filterLocation").performTextInput("Calgary")
        composeRule.onNodeWithTag("campsiteRow_Alpha").assertExists()
    }

    @Test fun filterByLocation() {
        composeRule.onNodeWithTag("filterLocation").performTextInput("Edmonton")
        composeRule.onNodeWithTag("filterApply").performClick()
        composeRule.onNodeWithTag("campsiteRow_Alpha").assertExists()
        composeRule.onNodeWithTag("campsiteRow_Beta").assertDoesNotExist()
    }

    @Test fun filterByCapacity() {
        composeRule.onNodeWithTag("filterCapacity").performTextInput("5")
        composeRule.onNodeWithTag("filterApply").performClick()
        composeRule.onNodeWithTag("campsiteRow_Alpha").assertExists()
        composeRule.onNodeWithTag("campsiteRow_Beta").assertDoesNotExist()
    }

    @Test fun combinedFilterWithNoMatchesShowsZero() {
        composeRule.onNodeWithTag("filterLocation").performTextInput("Edmonton")
        composeRule.onNodeWithTag("filterCapacity").performTextInput("11")
        composeRule.onNodeWithTag("filterApply").performClick()
        composeRule.onNodeWithText("Displaying 0 campsites").assertExists()
    }

    @Test fun resetShowsAllCampsitesAgain() {
        composeRule.onNodeWithTag("filterLocation").performTextInput("Edmonton")
        composeRule.onNodeWithTag("filterApply").performClick()
        composeRule.onNodeWithTag("campsiteRow_Beta").assertDoesNotExist()
        composeRule.onNodeWithTag("filterReset").performClick()
        composeRule.onNodeWithTag("campsiteRow_Alpha").assertExists()
        composeRule.onNodeWithTag("campsiteRow_Beta").assertExists()
    }
}