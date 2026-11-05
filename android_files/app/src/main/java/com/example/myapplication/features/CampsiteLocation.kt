package com.example.myapplication.features

import android.location.Geocoder
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
import java.io.IOException
import java.util.Locale

/**
 * Purpose: Store a Campsite address and convert it to a GeoPoint.
 * @param address: An address which will be converted to a geopoint
 * @param geoPoint: Optional | Holds the lat/lon of the address once it is found
 * Citation: For implementation https://stackoverflow.com/questions/63030562/convert-address-to-geo-coordinates-in-android
 *
 */
class CampsiteLocation(
    var address: String = "",
    var geoPoint: GeoPoint? = null
) {

    // Not saved to Firestore
    @get:Exclude
    var isValid: Boolean = false

    /**
     * Looks up the address and fills in the geoPoint.
     * Call this when the owner enters an address, not when loading from Firestore.
     *
     * @return true if the address was found, false if not
     */
    fun lookUpAddress(): Boolean {
        try {
            val geocoder = Geocoder(MyApplication.instance, Locale.ENGLISH)
            val matches = geocoder.getFromLocationName(address, 1)

            if (matches == null || matches.isEmpty()) {
                isValid = false
                return false
            }

            val match = matches[0]
            geoPoint = GeoPoint(match.latitude, match.longitude)
            isValid = true
            return true
        } catch (e: IOException) {
            isValid = false
            return false
        }
    }
}