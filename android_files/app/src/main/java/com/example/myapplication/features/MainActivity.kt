package com.example.myapplication.features

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings.Secure
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.ui_components.theme.MyApplicationTheme
import com.example.myapplication.ui_components.CampsiteApp
import androidx.compose.runtime.LaunchedEffect
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
//                LaunchedEffect(Unit) {
//                    while (true) {
//                        lotteryRepository.drawClosedLotteries()
//                        delay(60_000)   // check once a minute
//                    }
//                }
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
                    lotteryRepository = lotteryRepository,
                    joinWaitlist = lotteryRepository::joinLotteryWaitlist

                )
            }
        }
    }
}