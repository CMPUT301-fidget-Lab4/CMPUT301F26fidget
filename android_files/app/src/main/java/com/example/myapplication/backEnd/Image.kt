package com.example.myapplication.features
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


class Image (imageURI:  Uri? = null ) {

    // To Show a ImageURI need the composble to remember the current state
    var imageURI by mutableStateOf<Uri?>(imageURI)

    fun changeUri(newUri: Uri) {
        imageURI = newUri
    }
}