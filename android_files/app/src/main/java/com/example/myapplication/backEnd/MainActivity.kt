package com.example.myapplication.backEnd

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings.Secure
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.frontEnd.theme.MyApplicationTheme
import com.example.myapplication.frontEnd.viewcontroller.CampsiteApp
import com.example.myapplication.backEnd.firebaseRepositories.BookingScheduleRepository
import com.example.myapplication.backEnd.firebaseRepositories.CampsiteRepository
import com.example.myapplication.backEnd.firebaseRepositories.LotteryRepository
import com.example.myapplication.backEnd.firebaseRepositories.NotificationRepository
import com.example.myapplication.backEnd.firebaseRepositories.ReservationRepository
import com.example.myapplication.backEnd.firebaseRepositories.UserRepository
import com.example.myapplication.backEnd.firebaseRepositories.MessageRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        @SuppressLint("HardwareIds")
        val currentDeviceID: String = Secure.getString(this.contentResolver, Secure.ANDROID_ID)
        val scheduleRepository = BookingScheduleRepository()
        val reservationRepository = ReservationRepository(scheduleRepository)
        val userRepository = UserRepository(currentDeviceID)
        val notificationRepository = NotificationRepository()
        val messageRepository = MessageRepository()
        lateinit var campsiteRepository: CampsiteRepository
        val lotteryRepository = LotteryRepository(campsiteLookup = { campsiteRepository.campsitesMap[it] }, userLookup = userRepository::findUser)
        campsiteRepository = CampsiteRepository(sharedLotteryRepository = lotteryRepository)

        setContent {
            MyApplicationTheme {
                AppEffects(
                    userRepository = userRepository,
                    campsiteRepository = campsiteRepository,
                    lotteryRepository = lotteryRepository,
                    notificationRepository = notificationRepository
                )
                CampsiteApp(
                    user = userRepository.currentUser,
                    users = userRepository.users,
                    onAddUser = userRepository::addUser,
                    onDeleteUser = userRepository::deleteUser,
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
                    notificationRepository = notificationRepository,
                    messageRepository = messageRepository
                )
            }
        }
    }
}
