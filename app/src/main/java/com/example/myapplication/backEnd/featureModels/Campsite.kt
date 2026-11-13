package com.example.myapplication.backEnd.featureModels

import com.google.firebase.firestore.Exclude
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf


/**
 * Represents a campsite that can be owned by a campsite owner
 * and managed or edited by an authorized administrator.
 * Stores the campsite's information, including its location,
 * capacity, owner, rating, and terms and conditions.
 *
 * @param name The campsite's name.
 * @param location The campsite's address and optional coordinates, or null when unset.
 * @param description A description of the campsite and its features.
 * @param occupancy The maximum number of people the campsite can accommodate.
 * @param photo The URL or reference to the campsite's photo.
 * @param tags Keywords or categories describing the campsite.
 * @param ownerID The identifier of the user who owns the campsite.
 * @param rating The campsite's rating, defaulting to 0.0.
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
    /**
     * Current and past lotteries loaded live from this campsite's lottery subcollection.
     * Write through LotteryRepository; these objects are not duplicated in the parent document.
     */
    @get:Exclude
    var lotteryCollection: Collection<Lottery>? by mutableStateOf(null)

    /**
     * Live bookingSchedules documents for every lottery involving this campsite.
     * Each schedule includes allowed stay windows and the bookings made across its campsites.
     * LotteryRepository and ReservationRepository write Firebase; listeners refresh this view.
     */
    @get:Exclude
    var bookingSchedules: MutableList<BookingSchedule<*>>? by mutableStateOf(null)

    var inLottery: Boolean = false

}
