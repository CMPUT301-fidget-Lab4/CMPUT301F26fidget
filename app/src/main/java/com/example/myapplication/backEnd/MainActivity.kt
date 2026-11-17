package com.example.myapplication.backEnd

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.provider.Settings.Secure
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.frontEnd.theme.MyApplicationTheme
import com.example.myapplication.frontEnd.viewcontroller.CampsiteApp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.example.myapplication.backEnd.firebaseRepositories.BookingScheduleRepository
import com.example.myapplication.backEnd.firebaseRepositories.CampsiteRepository
import com.example.myapplication.backEnd.firebaseRepositories.LotteryRepository
import com.example.myapplication.backEnd.firebaseRepositories.ReservationRepository
import com.example.myapplication.backEnd.firebaseRepositories.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        @SuppressLint("HardwareIds")
        val currentDeviceID: String = Secure.getString(this.contentResolver, Secure.ANDROID_ID)
        val scheduleRepository = BookingScheduleRepository()
        val reservationRepository = ReservationRepository(scheduleRepository)
        val userRepository = UserRepository(currentDeviceID)
        lateinit var campsiteRepository: CampsiteRepository
        val lotteryRepository = LotteryRepository(campsiteLookup = { campsiteRepository.campsitesMap[it] }, userLookup = userRepository::findUser)
        campsiteRepository = CampsiteRepository(sharedLotteryRepository = lotteryRepository)

        setContent {
            MyApplicationTheme {
                LaunchedEffect(lotteryRepository, userRepository) {
                    androidx.compose.runtime.snapshotFlow { userRepository.users.toList() }.collect { lotteryRepository.refreshReferences() }
                }
                DisposableEffect(campsiteRepository) { onDispose { campsiteRepository.close() } }
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
                    lotteryRepository = lotteryRepository,
                    onDeleteCampsite = campsiteRepository::deleteCampsite
                )
            }
        }
    }
}