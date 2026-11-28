package com.example.myapplication.lottery

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.theme.MyApplicationTheme
import com.example.myapplication.frontEnd.viewcontroller.CreateLotteryController
import com.example.myapplication.frontEnd.viewcontroller.CreateLotteryFlow
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreateLotteryNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun androidBackReturnsFromBookingDatesToTheFormBeforeLeavingTheWizard() {
        val owner = User(deviceID = "owner", userType = "campsite owner")
        val campsites = mapOf("Pine" to Campsite(name = "Pine"))
        val repository = mockk<LotteryRepository>()
        every { repository.availableCampsiteNames(owner, campsites) } returns listOf("Pine")
        every {
            repository.validateLotteryOptions(owner, campsites, any(), any(), any(), any(), any(), any(), any(), any())
        } returns null
        val controller = CreateLotteryController(repository)

        compose.setContent {
            MyApplicationTheme(dynamicColor = false) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "MainScreen") {
                    composable("MainScreen") {
                        Button(onClick = { nav.navigate("CreateLottery") }) { Text("New lottery") }
                    }
                    composable("CreateLottery") {
                        CreateLotteryFlow(nav, controller, owner, campsites, initialCampsiteName = "Pine")
                    }
                }
            }
        }
        compose.onNodeWithText("New lottery").performClick()
        compose.onNodeWithTag("createLotteryNextButton").performScrollTo().performClick()
        compose.onNodeWithTag("pickBookingPeriodsTitle").assertIsDisplayed()

        pressBack()
        compose.onNodeWithTag("createLotteryCampsiteDropdown").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pine").assertExists()
        pressBack()
        compose.onNodeWithText("New lottery").assertIsDisplayed()
    }
}
