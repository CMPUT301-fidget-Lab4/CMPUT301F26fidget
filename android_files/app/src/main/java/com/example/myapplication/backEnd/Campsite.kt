package com.example.myapplication.backEnd


/**
 * Purpose: Create campsite with the needed requirements,so  that a owner/adim can own/edit the given campsite.
 * @param campsiteName : The wanted name CampsiteName.
 * @param location : CampsiteLocation is an object or null .
 * @param occupancy : This is the max amount of people allowed to be at a given campsite.
 * @param description: This is 250 word max string description of the campsite.
 * @param imageUrl: This is a class that holds the image(Uri) or can be null.
 * @param termCondition:  This a 250 word amount of words of a given campsite terms/conditions are.
 * */
class Campsite(
    val campsiteName: String,
    val location: CampsiteLocation? = null,
    val description: String,
    val termCondition: String,
    val occupancy: Short,
    var imageUrl: Image? = null ,
    var hasLotteryWinner: Boolean = false
) {

    var lottery : Lottery = TODO()


    fun setCampsiteImage(newImage: Image) {
        imageUrl = newImage
    }

//}  public fun RegistrationPeriod(start: Date, end: Date)
//{
//
//}
//
//public fun BookingPeriod( )
//{       TODO("develop logic " )
//}

}



