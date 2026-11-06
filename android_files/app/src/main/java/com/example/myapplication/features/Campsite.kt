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
) {
    @get:Exclude
    var lotteryCollection: Collection<Lottery>? = null

    @get:Exclude
    var bookingSchedules: MutableList<BookingSchedule>? = null

    @get:Exclude
    var inLottery = false
}
//    /**
//     * Sets the lottery's booking start and end date/time.
//     *
//     * A lottery and registration period must already exist.
//     * The booking period cannot start in the past or before
//     * registration ends.
//     *
//     * @param start The booking start date and time.
//     * @param end The booking end date and time.
//     * @return true if the booking period was set, false otherwise.
//     */
//    fun setBookingPeriod(
//        start: LocalDateTime,
//        end: LocalDateTime
//    ): Boolean {
//
//        // Check that a lottery exists
//        if (!inLottery || lottery.id.isBlank()) {
//            return false
//        }
//
//        // Registration must already be set
//        if (!reservationSet) {
//            return false
//        }
//
//        // Booking cannot start in the past
//        if (start.isBefore(LocalDateTime.now())) {
//            return false
//        }
//
//        // Booking end must be AFTER booking start
//        if (!end.isAfter(start)) {
//            return false
//        }
//
//        // Booking cannot start before registration ends
//        if (start.isBefore(lottery.period.registrationEnd)) {
//            return false
//        }
//
//        // Update locally
//        lottery.period.bookingStart = start
//        lottery.period.bookingEnd = end
//
//        bookingSet = true
//
//        return true
//    }





