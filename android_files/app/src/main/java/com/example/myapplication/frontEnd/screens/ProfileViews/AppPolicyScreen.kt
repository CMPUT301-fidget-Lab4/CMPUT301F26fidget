package com.example.myapplication.frontEnd.screens.profileViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun AppPolicyScreen(
    navController: NavController,

){

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("App Policy & Guidelines")

        Spacer(modifier = Modifier.height(16.dp))

        Text("This will contain the App Policy and Guidelines when It is ready.", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                navController.popBackStack()
            },
            modifier = Modifier.testTag("PolicyMenuBack")
        ) {
            Text("Back")
        }




    }

}