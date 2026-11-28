package com.example.myapplication.campsite

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.frontEnd.screens.campsiteViews.CampsiteListScreen
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.campsite.CampsiteLocation
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
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
        displayCount(2)
        scrollToTag("campsiteRow_Alpha").assertExists()
        scrollToTag("campsiteRow_Beta").assertExists()
    }

    @Test fun typingDoesNotFilterUntilButtonPressed() {
        scrollToTag("filterLocation").performTextInput("Calgary")
        displayCount(2)
        scrollToTag("campsiteRow_Alpha").assertExists()
    }

    @Test fun filterByLocation() {
        scrollToTag("filterLocation").performTextInput("Edmonton")
        scrollToTag("filterApply").performClick()
        displayCount(1)
        scrollToTag("campsiteRow_Alpha").assertExists()
    }

    @Test fun filterByCapacity() {
        scrollToTag("filterCapacity").performTextInput("5")
        scrollToTag("filterApply").performClick()
        displayCount(1)
        scrollToTag("campsiteRow_Alpha").assertExists()
    }

    @Test fun combinedFilterWithNoMatchesShowsZero() {
        scrollToTag("filterLocation").performTextInput("Edmonton")
        scrollToTag("filterCapacity").performTextInput("11")
        scrollToTag("filterApply").performClick()
        displayCount(0)
    }

    @Test fun resetShowsAllCampsitesAgain() {
        scrollToTag("filterLocation").performTextInput("Edmonton")
        scrollToTag("filterApply").performClick()
        displayCount(1)
        scrollToTag("filterReset").performClick()
        displayCount(2)
        scrollToTag("campsiteRow_Alpha").assertExists()
        scrollToTag("campsiteRow_Beta").assertExists()
    }

    private fun scrollToTag(tag: String): SemanticsNodeInteraction {
        scrollTo(hasTestTag(tag))
        return composeRule.onNodeWithTag(tag)
    }

    private fun displayCount(count: Int) {
        val summary = "Displaying $count campsites"
        scrollTo(hasText(summary))
        composeRule.onNodeWithText(summary).assertExists()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNodeWithTag("campsiteListScroll").performScrollToNode(matcher)
    }
}
