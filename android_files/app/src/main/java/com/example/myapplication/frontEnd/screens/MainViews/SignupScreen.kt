package com.example.myapplication.frontEnd.screens.mainViews

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController
import com.example.myapplication.features.profile.User
import com.example.myapplication.frontEnd.theme.AdaptiveContent
import com.example.myapplication.frontEnd.theme.AppLayout

private data class AccountTypeOption(val value: String, val label: String, val testTag: String)

private val accountTypeOptions = listOf(
    AccountTypeOption("entrant", "Entrant", "entrantButton"),
    AccountTypeOption("campsite owner", "Campsite Owner", "campsiteOwnerButton"),
    AccountTypeOption("admin", "Admin", "adminButton")
)

/** Registers a user for the current device and opens their main screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Signup(
    navController: NavController,
    onAddUser: (User) -> Unit,
    getAndroidId: (Context) -> String
) {
    var userName by rememberSaveable { mutableStateOf("") }
    var userPhoneNumber by rememberSaveable { mutableStateOf("") }
    var userEmail by rememberSaveable { mutableStateOf("") }
    var userType by rememberSaveable { mutableStateOf("") }
    var incompleteEntry by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    AdaptiveContent(maxWidth = AppLayout.FormWidth) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppLayout.Medium),
            verticalArrangement = Arrangement.spacedBy(AppLayout.Medium, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sign Up", style = MaterialTheme.typography.headlineSmall)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppLayout.Small, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(AppLayout.Small)
            ) {
                accountTypeOptions.forEach { option ->
                    val selectedType = userType == option.value
                    Button(
                        onClick = { userType = option.value },
                        colors = if (selectedType) ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) else ButtonDefaults.buttonColors(),
                        modifier = Modifier.testTag(option.testTag).semantics { selected = selectedType }
                    ) {
                        Text(option.label, textAlign = TextAlign.Center)
                    }
                }
            }

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text("Enter Name") },
                modifier = Modifier.fillMaxWidth().testTag("nameTextField")
            )

            OutlinedTextField(
                value = userEmail,
                onValueChange = { userEmail = it },
                label = { Text("Enter Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().testTag("emailTextField")
            )

            OutlinedTextField(
                value = userPhoneNumber,
                onValueChange = { userPhoneNumber = it },
                label = { Text("Enter Phone Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().testTag("phoneNumberTextField")
            )

            if (incompleteEntry) {
                Text("Complete all fields to sign up", color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    if (userName.isEmpty() || userType.isEmpty() || userPhoneNumber.isEmpty() || userEmail.isEmpty()) {
                        incompleteEntry = true
                        return@Button
                    }
                    onAddUser(User(userType, userName, userEmail, userPhoneNumber, getAndroidId(context)))
                    navController.navigate("MainScreen") {
                        // Completed signup must not remain reachable with Back.
                        popUpTo("HomeScreen") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.testTag("createAccountButton")
            ) {
                Text("Create account")
            }
        }
    }
}
