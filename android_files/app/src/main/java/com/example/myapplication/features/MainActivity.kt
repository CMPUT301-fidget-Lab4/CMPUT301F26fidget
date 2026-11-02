package com.example.myapplication.features

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings.Secure
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.ui_components.theme.MyApplicationTheme
import com.example.myapplication.ui_components.CampsiteApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        @SuppressLint("HardwareIds")
        val currentDeviceID: String = Secure.getString(this.contentResolver, Secure.ANDROID_ID)
        val campsiteRepository = CampsiteRepository()
        val userRepository = UserRepository(currentDeviceID)

        setContent {
            MyApplicationTheme {
                CampsiteApp(
                    user = userRepository.currentUser,
                    onAddUser = userRepository::addUser,
                    onDeleteUser = userRepository::deleteUser,
                    onUpdateUser = userRepository::updateUser,
                    findUser = userRepository::findUser,
                    getAndroidId = userRepository::getDeviceID,
                    authState = userRepository.authState,
                    campsites = campsiteRepository.campsites,
                    findCampsite = campsiteRepository::findCampsite,
                    onAddCampsite = campsiteRepository::addCampsite
                )
            }
        }
    }
}