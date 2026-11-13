package com.example.myapplication

import android.location.Geocoder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.features.CampsiteLocation
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampsiteLocationTest {

    private val knownAddress =
        "1600 Amphitheatre Parkway, Mountain View, CA, USA"

    private val banffAddress =
        "Banff National Park, Alberta, Canada"

    private val nonsenseAddress =
        "asdkjhqwe zxcmnb 99999999"

    // Skip geocoding tests if geocoder is unavailable.
    private fun requireGeocoder() {
        assumeTrue(
            "This device does not provide a geocoder.",
            Geocoder.isPresent()
        )
    }

    @Test
    fun knownAddress_isValid() {
        requireGeocoder()

        val location = CampsiteLocation(knownAddress)

        val result = location.lookUpAddress()

        assertTrue(result)
        assertTrue(location.isValid)
    }

    @Test
    fun knownAddress_setsGeoPoint() {
        requireGeocoder()

        val location = CampsiteLocation(knownAddress)

        assertTrue(location.lookUpAddress())
        assertNotNull(location.geoPoint)
    }

    @Test
    fun knownAddress_geoPointIsNearExpectedCoordinates() {
        requireGeocoder()

        val location = CampsiteLocation(knownAddress)

        assertTrue(location.lookUpAddress())

        // Google HQ is roughly at 37.42, -122.08.
        assertEquals(37.42, location.geoPoint!!.latitude, 0.1)
        assertEquals(-122.08, location.geoPoint!!.longitude, 0.1)
    }

    @Test
    fun nonsenseAddress_isNotValid() {
        requireGeocoder()

        val location = CampsiteLocation(nonsenseAddress)

        val result = location.lookUpAddress()

        assertFalse(result)
        assertFalse(location.isValid)
    }

    @Test
    fun nonsenseAddress_leavesGeoPointNull() {
        requireGeocoder()

        val location = CampsiteLocation(nonsenseAddress)

        assertFalse(location.lookUpAddress())
        assertNull(location.geoPoint)
    }

    @Test
    fun lookUpAddress_knownAddress_returnsTrue() {
        requireGeocoder()

        val location = CampsiteLocation(banffAddress)

        assertTrue(location.lookUpAddress())
    }

    @Test
    fun lookUpAddress_nonsenseAddress_returnsFalse() {
        requireGeocoder()

        val location = CampsiteLocation(nonsenseAddress)

        assertFalse(location.lookUpAddress())
    }

    @Test
    fun lookUpAddress_validAddress_updatesGeoPoint() {
        requireGeocoder()

        val location = CampsiteLocation(banffAddress)

        assertTrue(location.lookUpAddress())
        assertNotNull(location.geoPoint)
        assertTrue(location.isValid)
    }

    @Test
    fun invalidAddress_doesNotChangeGeoPointPassedIn() {
        requireGeocoder()

        val start = GeoPoint(1.0, 2.0)
        val location = CampsiteLocation(nonsenseAddress, start)

        assertFalse(location.lookUpAddress())

        assertEquals(1.0, location.geoPoint!!.latitude, 0.0)
        assertEquals(2.0, location.geoPoint!!.longitude, 0.0)
        assertFalse(location.isValid)
    }

    @Test
    fun validAddress_overwritesGeoPointPassedIn() {
        requireGeocoder()

        val start = GeoPoint(1.0, 2.0)
        val location = CampsiteLocation(knownAddress, start)

        assertTrue(location.lookUpAddress())

        assertTrue(location.geoPoint!!.latitude != 1.0)
        assertTrue(location.isValid)
    }

    @Test
    fun constructorStoresAddress() {
        val location = CampsiteLocation(banffAddress)

        assertEquals(banffAddress, location.address)
    }

    @Test
    fun emptyAddress_isNotValid() {
        val location = CampsiteLocation("")

        assertFalse(location.isValid)
        assertNull(location.geoPoint)
    }

    // NEW TESTS FOR UPDATED CLASS

    @Test
    fun constructor_doesNotAutomaticallyValidateAddress() {
        val location = CampsiteLocation(knownAddress)

        assertFalse(location.isValid)
        assertNull(location.geoPoint)
    }

    @Test
    fun constructor_withGeoPoint_storesGeoPoint() {
        val point = GeoPoint(53.5461, -113.4937)

        val location = CampsiteLocation("Edmonton, Alberta", point)

        assertEquals(point, location.geoPoint)
        assertFalse(location.isValid)
    }

    @Test
    fun defaultConstructor_hasDefaultValues() {
        val location = CampsiteLocation()

        assertEquals("", location.address)
        assertNull(location.geoPoint)
        assertFalse(location.isValid)
    }

    @Test
    fun changingAddress_doesNotAutomaticallyCallGeocoder() {
        val location = CampsiteLocation()

        location.address = knownAddress

        assertEquals(knownAddress, location.address)
        assertFalse(location.isValid)
        assertNull(location.geoPoint)
    }
}