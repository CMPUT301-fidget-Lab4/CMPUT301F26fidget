package com.example.myapplication

import java.util.Date

/**
 * Purpose: Create  campsite with the needed descriptions that a owner can own or a admin own or edit.
 * @param String : The wanted name CampsiteName.
 * @param CampsiteLocation : CampsiteLocation object takes String or optional geoPoint
 * @
 * */
class Campsite(campsiteName: String, location: CampsiteLocation? = null, occupancy: Short, photo: String )
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