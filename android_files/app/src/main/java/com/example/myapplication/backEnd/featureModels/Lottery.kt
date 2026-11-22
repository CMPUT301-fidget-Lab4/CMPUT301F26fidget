package com.example.myapplication.backEnd.featureModels

import com.example.myapplication.backEnd.userModels.User
import com.google.firebase.firestore.Exclude

/** A typed link to bookingSchedules/{id}; it never contains a Lottery or duplicate bookings. */
data class BookingScheduleReference(val id: String)

/** Maps retain membership even when a canonical campsite/account has not loaded or was deleted. */
data class Lottery(
    var id: String = "",
    var ownerId: String = "",
    @get:Exclude var campsites: Map<String, Campsite?>,
    var period: Registration = Registration(),
    var numWinners: Int = 1,
    var waitlistCapacity: Int = 0,
    @get:Exclude var waitlist: Map<String, User?> = emptyMap(),
    var winners: List<String> = emptyList(),
    var drawn: Boolean = false,
    @get:Exclude val bookingSchedule: BookingScheduleReference = BookingScheduleReference(id)
) {
    /** Compatibility for ID-based documents and fixtures; no second membership collection is stored. */
    constructor(
        id: String = "", ownerId: String = "", campsiteIds: List<String> = emptyList(),
        period: Registration = Registration(), numWinners: Int = 1, waitlistCapacity: Int = 0,
        waitlist: List<String> = emptyList(), winners: List<String> = emptyList(), drawn: Boolean = false,
        bookingSchedule: BookingScheduleReference = BookingScheduleReference(id)
    ) : this(id, ownerId, campsiteIds.associateWith { null }, period, numWinners, waitlistCapacity,
        waitlist.associateWith { null }, winners, drawn, bookingSchedule)

    @get:Exclude
    var campsiteIds: List<String>
        get() = campsites.keys.toList()
        set(ids) { campsites = ids.associateWith { campsites[it] } }

    @get:Exclude
    val waitlistIds: List<String> get() = waitlist.keys.toList()
}
