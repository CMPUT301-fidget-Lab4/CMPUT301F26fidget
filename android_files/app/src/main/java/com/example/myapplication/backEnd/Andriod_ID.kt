package com.example.myapplication.features

class AndroidID {

    fun getAndroidID(): String {
       return (10000..99999).random().toString()
    }
}