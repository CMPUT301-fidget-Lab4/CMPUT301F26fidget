package com.example.myapplication.backEnd.featureModels

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Purpose: To hold an image.
***/
class Image (imageURI:  Uri? = null ) {

    var imageURI by mutableStateOf<Uri?>(imageURI)

    fun changeUri(newUri: Uri) {
        imageURI = newUri
    }
}