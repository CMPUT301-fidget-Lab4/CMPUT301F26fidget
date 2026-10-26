package com.example.myapplication.Features

import android.app.Application

/**
 * Purpose: This Class is needed for Geolocation and later classes.
 *          To use some packages it needs a reference to the application activity
 *          which can be either be done within mainactivity or now classes can
 *          reference the application activity.
 *
 *
 * */
class MyApplication : Application() {

    // This allows other classes access to this Application object
    companion object {
        lateinit var instance: MyApplication
            private set
    }

    // This function allows access to application dependent process
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}