
package com.example.myapplication

import com.example.myapplication.features.JoinedLottery
import com.example.myapplication.features.Lottery
import com.example.myapplication.features.LotteryRepository
import com.example.myapplication.features.Registration
import com.example.myapplication.features.User

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

import java.time.LocalDateTime

/**
 * Tests LotteryRepository validation and User lottery records.
 *
 * Uses mocked Firestore references without accessing the real database.
 *
 * Successful Firestore transactions and winner drawing are not tested here.
 */
class LotteryWinnerTest {

    private val opens =
        LocalDateTime.of(2035, 6, 9, 17, 0)

    private val closing =
        LocalDateTime.of(2035, 6, 10, 17, 0)

    private lateinit var lotteryRepository: LotteryRepository

    /**
     * Creates a mocked Firestore instance before each test.
     */
    @Before
    fun setUp() {
        val db = Mockito.mock(FirebaseFirestore::class.java)

        val campsiteCollection =
            Mockito.mock(CollectionReference::class.java)

        Mockito.`when`(db.collection("campsites"))
            .thenReturn(campsiteCollection)

        lotteryRepository = LotteryRepository(db)
    }

    /**
     * Creates a dummy Lottery for validation tests.
     */
    private fun lottery(
        waitlist: List<String> = emptyList(),
        numWinners: Int = 2,
        waitlistCapacity: Int = 0
    ): Lottery {
        return Lottery(
            id = "test-lottery",
            campsiteIds = listOf("CampA"),
            period = Registration(
                registrationStart = opens,
                registrationEnd = closing
            ),
            numWinners = numWinners,
            waitlistCapacity = waitlistCapacity,
            waitlist = waitlist,
            winners = emptyList(),
            drawn = false
        )
    }

    private fun entrant(id: String): User {
        return User(
            userType = "entrant",
            deviceID = id
        )
    }

    /**
     * Checks that a repository task was rejected immediately
     * with an IllegalArgumentException.
     */
    private fun assertRejected(task: Task<*>) {
        assertTrue(task.isComplete)
        assertFalse(task.isSuccessful)
        assertTrue(task.exception is IllegalArgumentException)
    }

    // ---------------------------------------------------------------
    // Joining the waitlist
    // ---------------------------------------------------------------

    /**
     * Campsite owners cannot join a lottery waitlist.
     */
    @Test
    fun campsiteOwnerCannotJoin() {
        val owner = User(
            userType = "campsite owner",
            deviceID = "owner1"
        )

        var result: Boolean? = null

        lotteryRepository.joinLotteryWaitlist(
            "CampA",
            owner
        ) { success ->
            result = success
        }

        assertEquals(false, result)
    }

    /**
     * A user without a device ID cannot join.
     */
    @Test
    fun cannotJoinWithoutDeviceId() {
        val user = entrant("")

        var result: Boolean? = null

        lotteryRepository.joinLotteryWaitlist(
            "CampA",
            user
        ) { success ->
            result = success
        }

        assertEquals(false, result)
    }

    /**
     * A lottery campsite ID cannot be empty.
     */
    @Test
    fun cannotJoinWithEmptyCampsiteName() {
        val user = entrant("user1")

        var result: Boolean? = null

        lotteryRepository.joinLotteryWaitlist(
            "",
            user
        ) { success ->
            result = success
        }

        assertEquals(false, result)
    }

    /**
     * A campsite document ID cannot contain "/".
     */
    @Test
    fun cannotJoinWithInvalidCampsiteName() {
        val user = entrant("user1")

        var result: Boolean? = null

        lotteryRepository.joinLotteryWaitlist(
            "Camp/A",
            user
        ) { success ->
            result = success
        }

        assertEquals(false, result)
    }

    /**
     * Users with unrecognized roles cannot join.
     */
    @Test
    fun unknownUserTypeCannotJoin() {
        val user = User(
            userType = "unknown",
            deviceID = "user1"
        )

        var result: Boolean? = null

        lotteryRepository.joinLotteryWaitlist(
            "CampA",
            user
        ) { success ->
            result = success
        }

        assertEquals(false, result)
    }

    // ---------------------------------------------------------------
    // Leaving the waitlist
    // ---------------------------------------------------------------

    /**
     * Users without device IDs cannot leave a waitlist.
     */
    @Test
    fun cannotLeaveWithoutDeviceId() {
        val user = entrant("")

        var result: Boolean? = null

        lotteryRepository.leaveLotteryWaitlist(
            "CampA",
            user
        ) { success ->
            result = success
        }

        assertEquals(false, result)
    }

    /**
     * Leaving requires a valid campsite name.
     */
    @Test
    fun cannotLeaveInvalidLottery() {
        val user = entrant("user1")

        var result: Boolean? = null

        lotteryRepository.leaveLotteryWaitlist(
            "",
            user
        ) { success ->
            result = success
        }

        assertEquals(false, result)
    }

    // ---------------------------------------------------------------
    // Lottery creation
    // ---------------------------------------------------------------

    /**
     * Cannot create a lottery if its campsite
     * is missing from the supplied map.
     */
    @Test
    fun cannotCreateLotteryWithoutCampsite() {
        val task = lotteryRepository.createLottery(
            campsiteName = "CampA",
            period = Registration(opens, closing),
            numWinners = 2,
            campsites = emptyMap()
        )

        assertRejected(task)
    }

    // ---------------------------------------------------------------
    // Lottery validation
    // ---------------------------------------------------------------

    /**
     * Number of winners must be at least one.
     */
    @Test
    fun cannotSaveLotteryWithZeroWinners() {
        val testLottery = lottery(numWinners = 0)

        val task = lotteryRepository.saveLottery(
            "CampA",
            testLottery
        )

        assertRejected(task)
    }

    /**
     * Negative waitlist capacities are invalid.
     */
    @Test
    fun cannotSaveLotteryWithNegativeCapacity() {
        val testLottery = lottery(waitlistCapacity = -1)

        val task = lotteryRepository.saveLottery(
            "CampA",
            testLottery
        )

        assertRejected(task)
    }

    /**
     * Registration end must be after registration start.
     */
    @Test
    fun cannotSaveLotteryWithInvalidDates() {
        val testLottery = lottery().copy(
            period = Registration(
                registrationStart = opens,
                registrationEnd = opens
            )
        )

        val task = lotteryRepository.saveLottery(
            "CampA",
            testLottery
        )

        assertRejected(task)
    }

    /**
     * A lottery must have a valid ID.
     */
    @Test
    fun cannotSaveLotteryWithoutId() {
        val testLottery = lottery().copy(id = "")

        val task = lotteryRepository.saveLottery(
            "CampA",
            testLottery
        )

        assertRejected(task)
    }

    // ---------------------------------------------------------------
    // Loading and deleting
    // ---------------------------------------------------------------

    /**
     * Loading an invalid campsite returns null immediately.
     */
    @Test
    fun cannotLoadInvalidLottery() {
        var result: Lottery? = lottery()

        lotteryRepository.getLottery("") {
            result = it
        }

        assertNull(result)
    }

    /**
     * Deleting an invalid campsite is rejected.
     */
    @Test
    fun cannotDeleteInvalidLottery() {
        val task = lotteryRepository.deleteLottery("")

        assertRejected(task)
    }

    // ---------------------------------------------------------------
    // User joined lotteries
    // ---------------------------------------------------------------

    /**
     * Checks the new joinedLotteries list in User.
     */
    @Test
    fun userCanSeeJoinedLotteries() {
        val user = entrant("user123")

        user.joinedLotteries = listOf(
            JoinedLottery("lottery1", "CampA"),
            JoinedLottery("lottery2", "CampB")
        )

        assertEquals(2, user.joinedLotteries.size)

        assertTrue(
            user.joinedLotteries.contains(
                JoinedLottery("lottery1", "CampA")
            )
        )

        assertTrue(
            user.joinedLotteries.contains(
                JoinedLottery("lottery2", "CampB")
            )
        )
    }

    /**
     * Checks that a user can have a lottery removed
     * from their local joinedLotteries list.
     */
    @Test
    fun userJoinedLotteryCanBeRemovedLocally() {
        val user = entrant("user123")

        user.joinedLotteries = listOf(
            JoinedLottery("lottery1", "CampA"),
            JoinedLottery("lottery2", "CampB")
        )

        user.joinedLotteries =
            user.joinedLotteries.filterNot {
                it.lotteryId == "lottery1"
            }

        assertEquals(1, user.joinedLotteries.size)

        assertEquals(
            "lottery2",
            user.joinedLotteries.first().lotteryId
        )
    }
}
