package com.example.myapplication.features

import android.location.Geocoder
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
)
{

    fun addressExists(address: String): Boolean {
        return try {
            val geocoder = Geocoder(MyApplication.instance, Locale.ENGLISH)
            val matches = geocoder.getFromLocationName(address, 1)
            if (matches == null) {
                false
            } else if (matches.isEmpty()) {
                false
            } else {
                val match = matches[0]
                // Store the founded value
                geoPoint = GeoPoint(match.latitude, match.longitude)
                true
            }

        } catch (_: IOException) {
            false
        }
    }

    val  isValid  = addressExists(address )

    }