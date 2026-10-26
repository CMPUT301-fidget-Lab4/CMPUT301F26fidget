package com.example.myapplication

import android.location.Geocoder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampsiteLocationTest {

    @Before
    fun checkGeocoderAvailable() {
        assumeTrue(
            "This device does not provide a geocoder.",
            Geocoder.isPresent()
        )
    }

    @Test
    fun addressExists_knownAddress_returnsTrue() {
        val address =
            "1600 Amphitheatre Parkway, Mountain View, CA, USA"

        val location = CampsiteLocation(address = address)

        assertTrue(location.addressExists(address))
        assertTrue(location.isValid)
    }

    @Test
    fun addressExists_emptyAddress_returnsFalse() {
        val location = CampsiteLocation(address = "")

        assertFalse(location.addressExists(""))
        assertFalse(location.isValid)
    }
    @Test
    fun myaddressTest()
    {
       val  address =  "9707 185 st"
        val location = CampsiteLocation(address = "9707 185 st ")

        assertTrue(location.addressExists(address))
        assertTrue(location.isValid)
        Log.d("CampsiteTest", location.geoPoint.toString())
        // https://www.gps-coordinates.net/ This site confirms that what I have is correct



    }
}