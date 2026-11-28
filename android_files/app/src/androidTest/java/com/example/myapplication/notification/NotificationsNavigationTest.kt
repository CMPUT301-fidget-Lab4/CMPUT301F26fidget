package com.example.myapplication.notification

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.notification.AppNotification
import com.example.myapplication.features.profile.AuthState
import com.example.myapplication.data.firebase.BookingScheduleRepository
import com.example.myapplication.features.notification.LotteryNotificationInbox
import com.example.myapplication.data.firebase.LotteryRepository
import com.example.myapplication.data.firebase.MessageRepository
import com.example.myapplication.data.firebase.NotificationRepository
import com.example.myapplication.data.firebase.ReservationRepository
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.theme.MyApplicationTheme
import com.example.myapplication.frontEnd.viewcontroller.CampsiteApp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NotificationsNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun useAppWindowPolicy() {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
    }

    @Test
    fun touchBackIsBelowTheStatusBarAndReturnsToTheExistingDashboard() {
        var rootBacks = 0
        compose.setContent { notificationApp(onRootBack = { rootBacks++ }) }
        openInbox()

        val statusBarPx = compose.runOnIdle {
            val root = compose.activity.window.decorView
            requireNotNull(ViewCompat.getRootWindowInsets(root))
                .getInsets(WindowInsetsCompat.Type.statusBars()).top
        }
        val back = compose.onNodeWithTag("notificationsBack")
        val topPx = back.getUnclippedBoundsInRoot().top.value * compose.density.density
        assertTrue("The touch target must begin below the status bar.", topPx >= statusBarPx)
        back.performTouchInput { click() }
        compose.onNodeWithTag("viewNotifications").assertIsDisplayed()

        openInbox()
        pressBack()
        compose.onNodeWithTag("viewNotifications").assertIsDisplayed()
        pressBack()
        compose.runOnIdle { assertEquals("Dashboard must be the only authenticated root.", 1, rootBacks) }
    }

    @Test
    fun androidBackReturnsToTheRoleSpecificDashboard() {
        val owner = profile.copy(userType = "campsite owner")
        compose.setContent { notificationApp(owner) }
        openInbox()
        pressBack()

        compose.onNodeWithTag("createLotteryButton").assertExists()
        compose.onNodeWithTag("viewOwnedLotteries").assertExists()
        compose.onNodeWithTag("adminProfileBrowse").assertDoesNotExist()
    }

    @Test
    fun nativeBackReturnsToWelcomeWhenTheProfileIsRemoved() {
        val currentUser = mutableStateOf<User?>(profile)
        val auth = MutableStateFlow<AuthState>(AuthState.LoggedIn(profile))
        compose.setContent { notificationApp(currentUser.value, auth) }
        openInbox()
        compose.runOnIdle {
            currentUser.value = null
            auth.value = AuthState.NotRegistered
        }

        compose.onNodeWithTag("notificationsBack").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("welcomeSignUpButton").assertIsDisplayed()
    }

    @Test
    fun compactPhoneAtMaximumFontScaleCanScrollAndGoBack() {
        assertScrollableInbox(DpSize(320.dp, 480.dp), fontScale = 2f)
    }

    @Test
    fun expandedTabletViewportCanScrollAndGoBack() {
        assertScrollableInbox(DpSize(840.dp, 720.dp), fontScale = 1.3f)
    }

    @Test
    fun landscapeWindowAtLargeFontScaleCanScrollAndGoBack() {
        // Android can ignore app-requested orientation on large screens.
        if (compose.activity.resources.configuration.smallestScreenWidthDp < 600) {
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
        }
        assertScrollableInbox(DpSize(680.dp, 320.dp), fontScale = 1.5f)
    }

    private fun assertScrollableInbox(viewport: DpSize, fontScale: Float) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(viewport)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    notificationApp()
                }
            }
        }
        openInbox()
        compose.onNodeWithTag("notificationsList").performScrollToIndex(results.lastIndex)
        compose.onNodeWithText(results.last().message).assertIsDisplayed()
        compose.onNodeWithTag("notificationsBack").performClick()
        compose.onNodeWithTag("viewNotifications").performScrollTo().assertIsDisplayed()
    }

    private fun openInbox() {
        compose.onNodeWithTag("viewNotifications").performScrollTo().performClick()
        compose.onNodeWithTag("notificationsBack").assertIsDisplayed()
    }

    @Composable
    private fun notificationApp(
        user: User? = profile,
        auth: MutableStateFlow<AuthState> = remember { MutableStateFlow<AuthState>(AuthState.LoggedIn(profile)) },
        onRootBack: (() -> Unit)? = null
    ) {
        BackHandler(enabled = onRootBack != null) { onRootBack?.invoke() }
        val notificationRepository = remember {
            mockk<NotificationRepository>().also { repository ->
                every { repository.inbox } returns MutableStateFlow(
                    LotteryNotificationInbox(profile.deviceID, results, loading = false)
                )
            }
        }
        MyApplicationTheme(dynamicColor = false) {
            CampsiteApp(
                user = user,
                users = listOfNotNull(user),
                onAddUser = {},
                onDeleteUser = { error("No profile writes in this fixture") },
                onUpdateUser = { _, _ -> error("No profile writes in this fixture") },
                findUser = { id -> user?.takeIf { it.deviceID == id } },
                getJoinedLotteries = { emptyList() },
                getAllActiveLotteryWaitlists = { emptyList() },
                getLotteryWaitlist = { _, _ -> null },
                getAndroidId = { profile.deviceID },
                authState = auth,
                campsites = emptyMap(),
                reservationRepository = remember { mockk<ReservationRepository>(relaxed = true) },
                scheduleRepository = remember { mockk<BookingScheduleRepository>(relaxed = true) },
                lotteryRepository = remember { mockk<LotteryRepository>(relaxed = true) },
                notificationRepository = notificationRepository,
                messageRepository = remember { mockk<MessageRepository>(relaxed = true) }
            )
        }
    }

    private val profile = User(deviceID = "notification-test", name = "Alex", userType = "entrant")
    private val results = List(18) { index ->
        AppNotification(
            id = "outcome-$index",
            userId = profile.deviceID,
            type = "WIN",
            message = "Result ${index + 1}. Your invitation for the lakeside campsite is ready in Reservations."
        )
    }
}
