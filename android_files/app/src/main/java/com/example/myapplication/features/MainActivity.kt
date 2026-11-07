package com.example.myapplication.features

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.provider.Settings.Secure
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.ui_components.theme.MyApplicationTheme
import com.example.myapplication.ui_components.CampsiteApp
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        @SuppressLint("HardwareIds")
        val currentDeviceID: String = Secure.getString(this.contentResolver, Secure.ANDROID_ID)
        val campsiteRepository = CampsiteRepository()
        val scheduleRepository = BookingScheduleRepository()
        val reservationRepository = ReservationRepository(scheduleRepository)
        val userRepository = UserRepository(currentDeviceID)
        val lotteryRepository = LotteryRepository()

        setContent {
            MyApplicationTheme {
                // Runs immediately and again one minute after each check completes.
                // The Firestore draw transaction is safe if several devices do this.
                LaunchedEffect(lotteryRepository) {
                    while (true) {
                        val finished = CompletableDeferred<Unit>()
                        lotteryRepository.drawClosedLotteries()
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val newlyDrawn = task.result
                                    if (newlyDrawn.isNotEmpty()) {
                                        Log.i("LotteryAutoDraw", "Drew lotteries: $newlyDrawn")
                                    }
                                } else {
                                    Log.e(
                                        "LotteryAutoDraw",
                                        "Could not check closed lotteries",
                                        task.exception
                                    )
                                }
                                finished.complete(Unit)
                            }
                        finished.await() // Prevent overlapping checks.
                        delay(60_000L)
                    }
                }
                CampsiteApp(
                    user = userRepository.currentUser,
                    users = userRepository.users,
                    onAddUser = userRepository::addUser,
                    onDeleteUser = { user ->
                        campsiteRepository.deleteCampsitesByOwner(user.deviceID)
                        userRepository.deleteUser(user)
                    },
                    onUpdateCampsite = campsiteRepository::updateCampsiteDetails,
                    onUpdateUser = userRepository::updateUser,
                    findUser = userRepository::findUser,
                    getAndroidId = userRepository::getDeviceID,
                    authState = userRepository.authState,
                    campsites = campsiteRepository.campsitesMap,
                    findCampsite = campsiteRepository::findCampsite,
                    onAddCampsite = campsiteRepository::addCampsite,
                    reservationRepository = reservationRepository,
                    scheduleRepository = scheduleRepository,
                    lotteryRepository = lotteryRepository
                )
            }
        }
    }
}