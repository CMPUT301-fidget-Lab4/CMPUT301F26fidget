package com.example.myapplication.features

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.UIComponets.CampsiteApp
import com.example.myapplication.UIComponets.ImagePickerScreen
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val userRepository = UserRepository()
        val getAndroidID = AndroidID()

        setContent {
            MyApplicationTheme {
                ImagePickerScreen()
                CampsiteApp(
                    users = userRepository.users,
                    onAddUser = userRepository::addUser,
                    onDeleteUser = userRepository::deleteUser,
                    onUpdateUser = userRepository::updateUser,
                    findUser = userRepository::findUser,
                    getAndroidId = getAndroidID::getAndroidID
                )
            }
        }
    }
}