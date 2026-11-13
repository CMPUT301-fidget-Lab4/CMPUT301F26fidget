package com.example.myapplication.campsiteTests

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.backEnd.featureModels.Campsite
import com.example.myapplication.backEnd.featureModels.CampsiteLocation
import com.example.myapplication.backEnd.userModels.User
import com.example.myapplication.frontEnd.screens.campsiteViews.EditCampsiteScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A simple NavController that counts how many times popBackStack() is called,
 * so the test does not need MockK.
 */
class FakeNavController : NavController(ApplicationProvider.getApplicationContext()) {
    var popCount = 0

    override fun popBackStack(): Boolean {
        popCount++
        return true
    }
}

@RunWith(AndroidJUnit4::class)
class CampsitePolicyEditingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ownerCanEditRulesAndPolicyLinksWithoutChangingTheAddress() {
        val campsite = Campsite(name = "Pine", location = CampsiteLocation("Existing address"),
            description = "Forest", occupancy = 5, ownerID = "owner", termsAndConditions = "Old rules")
        val navController = FakeNavController()
        var saved: String? = null
        compose.setContent {
            EditCampsiteScreen(navController, User(userType = "campsite owner", deviceID = "owner"), campsite,
                onUpdateCampsite = { name, location, description, occupancy, _, policies ->
                    assertEquals("Pine", name)
                    assertEquals("Existing address", location.address)
                    assertEquals("Forest", description)
                    assertEquals(5, occupancy)
                    saved = policies
                })
        }
        compose.onNodeWithTag("editCampsitePolicies").performScrollTo().assertTextContains("Old rules")
        compose.onNodeWithTag("editCampsitePolicies").performTextReplacement("No fires.\nhttps://example.com/rules")
        compose.onNodeWithTag("saveCampsiteEdits").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("No fires.\nhttps://example.com/rules", saved) }
        assertEquals(1, navController.popCount)
    }
}