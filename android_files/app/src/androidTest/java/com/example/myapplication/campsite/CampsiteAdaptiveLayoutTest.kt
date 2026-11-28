package com.example.myapplication.campsite

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.screens.campsiteViews.RegisterCampsiteScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class CampsiteAdaptiveLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun compactRegistrationStacksAddressAndKeepsSubmitReachable() {
        showRegistration(DpSize(320.dp, 480.dp), fontScale = 1f)
        assertAddressFieldsStack()
        compose.onNodeWithTag("registerCampsiteButton").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun largeFontStacksAddressEvenInLandscape() {
        showRegistration(DpSize(800.dp, 420.dp), fontScale = 2f)
        assertAddressFieldsStack()
        compose.onNodeWithTag("registerCampsiteButton").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun roomyRegistrationCanKeepAddressFieldsSideBySide() {
        showRegistration(DpSize(800.dp, 420.dp), fontScale = 1f)

        val number = compose.onNodeWithTag("registerCampsiteAddressNumber").getUnclippedBoundsInRoot()
        val street = compose.onNodeWithTag("registerCampsiteStreetName").getUnclippedBoundsInRoot()
        assertTrue(number.right < street.left)
        assertTrue(number.bottom > street.top)
    }

    private fun assertAddressFieldsStack() {
        val number = compose.onNodeWithTag("registerCampsiteAddressNumber").getUnclippedBoundsInRoot()
        val street = compose.onNodeWithTag("registerCampsiteStreetName").getUnclippedBoundsInRoot()
        assertTrue(number.bottom < street.top)
    }

    private fun showRegistration(size: DpSize, fontScale: Float) {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.FontScale(fontScale)
            ) {
                RegisterCampsiteScreen(
                    navController = rememberNavController(),
                    userProfile = User(userType = "campsite owner", deviceID = "owner"),
                    onAddCampsite = {},
                    findCampsite = { null }
                )
            }
        }
    }
}
