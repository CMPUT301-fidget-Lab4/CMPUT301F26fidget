package com.example.myapplication.features

import com.example.myapplication.features.CampsiteLocation
import com.google.firebase.firestore.Exclude
import java.time.LocalDate
import java.time.LocalDateTime


/**
 * Represents a campsite that can be owned by a campsite owner
 * and managed or edited by an authorized administrator.
 * Stores the campsite's information, including its location,
 * capacity, owner, rating, and terms and conditions.
 *
 * @param name The unique name of the campsite.
 * @param location The [CampsiteLocation] object containing the
 * campsite's address and optional GeoPoint coordinates.
 * @param description A description of the campsite and its features.
 * @param occupancy The maximum number of people the campsite can accommodate.
 * @param photo The URL or reference to the campsite's photo.
 * @param tags Keywords or categories describing the campsite.
 * @param ownerID The device ID of the [User] who owns the campsite.
 * @param rating The campsite's rating, initially set to 0.0.
 * @param termsAndConditions The rules and conditions for using the campsite.
 */
class Campsite(
    val name: String = "",
    val location: CampsiteLocation? = null,
    val description: String = "",
    val occupancy: Int = 0,
    val photo: String = "",
    val tags: String = "",
    val ownerID: String = "",
    var rating: Double = 0.0,
    var termsAndConditions: String = ""
)
{
    @get:Exclude
    var lotteryCollection: Collection<Lottery>? = null

    @get:Exclude
    var bookingSchedules: MutableList<BookingSchedule<*>>? = null

    var inLottery: Boolean = false


}