package com.example.myapplication.features

import com.example.myapplication.features.CampsiteLocation
import java.util.Date

/**
 * Purpose: Create  campsite with the needed descriptions that a owner can own or a admin own or edit.
 * @param name : The wanted name CampsiteName.
 * @param location : [CampsiteLocation] object takes String or optional geoPoint
 * @param description
 * @param occupancy
 * @param photo
 * @param tags
 * @param ownerID
 * */
 class Campsite(
    val name: String = "",
    val location: CampsiteLocation? = null,
    val description: String = "",
    val occupancy: Int = 0,
    val photo: String = "",
    val tags : String = "",
    val ownerID : String = "" // Phone ID for User
 )
{



    public fun RegistrationPeriod(start: Date, end: Date)
    {
        TODO("develop logic and date class" )

    }
    ///
    public fun BookingPeriod( )
    {       TODO("develop logic " )
    }


}