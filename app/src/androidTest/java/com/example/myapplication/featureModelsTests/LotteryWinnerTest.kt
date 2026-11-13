package com.example.myapplication.featureModelsTests

import com.example.myapplication.backEnd.featureModels.Lottery
import com.example.myapplication.backEnd.featureModels.Registration
import com.example.myapplication.backEnd.firebaseRepositories.LotteryRepository
import com.example.myapplication.backEnd.userModels.JoinedLottery
import com.example.myapplication.backEnd.userModels.User
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert
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
        Assert.assertTrue(task.isComplete)
        Assert.assertFalse(task.isSuccessful)
        Assert.assertTrue(task.exception is IllegalArgumentException)
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

        Assert.assertEquals(false, result)
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

        Assert.assertEquals(false, result)
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

        Assert.assertEquals(false, result)
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

        Assert.assertEquals(false, result)
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

        Assert.assertEquals(false, result)
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

        Assert.assertEquals(false, result)
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

        Assert.assertEquals(false, result)
    }

    // ---------------------------------------------------------------
    // Lottery creation
    // ---------------------------------------------------------------

    /**
     * Cannot create a lottery if its campsite
     * selection is empty.
     */
    private fun createLotteryForValidation(
        lottery: Lottery,
        campsiteIds: List<String> = listOf("CampA")
    ): Task<Void> {
        val input = lottery.copy(campsites = campsiteIds.associateWith { null })
        val schedule = com.example.myapplication.backEnd.featureModels.BookingSchedule(input,
            closing.toLocalDate().plusDays(1).atStartOfDay(), closing.toLocalDate().plusDays(8).atStartOfDay())
        return lotteryRepository.createLottery(User(userType = "campsite owner", deviceID = "owner"), input.id,
            campsiteIds, input.period, input.numWinners, input.waitlistCapacity, schedule.bookingStart, schedule.bookingEnd)
    }

    @Test
    fun cannotCreateLotteryWithoutCampsite() {
        val task = createLotteryForValidation(lottery(), campsiteIds = emptyList())

        assertRejected(task)
    }

    // ---------------------------------------------------------------
    // Lottery validation
    // ---------------------------------------------------------------

    /**
     * Number of winners must be at least one.
     */
    @Test
    fun cannotCreateLotteryWithZeroWinners() {
        val testLottery = lottery(numWinners = 0)

        val task = createLotteryForValidation(testLottery)

        assertRejected(task)
    }

    /**
     * Negative waitlist capacities are invalid.
     */
    @Test
    fun cannotCreateLotteryWithNegativeCapacity() {
        val testLottery = lottery(waitlistCapacity = -1)

        val task = createLotteryForValidation(testLottery)

        assertRejected(task)
    }

    /**
     * Registration end must be after registration start.
     */
    @Test
    fun cannotCreateLotteryWithInvalidDates() {
        val testLottery = lottery().copy(
            period = Registration(
                registrationStart = opens,
                registrationEnd = opens
            )
        )

        val task = createLotteryForValidation(testLottery)

        assertRejected(task)
    }

    /**
     * A lottery must have a valid ID.
     */
    @Test
    fun cannotCreateLotteryWithoutId() {
        val testLottery = lottery().copy(id = "")

        val task = createLotteryForValidation(testLottery)

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

        Assert.assertNull(result)
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

        Assert.assertEquals(2, user.joinedLotteries.size)

        Assert.assertTrue(
            user.joinedLotteries.contains(
                JoinedLottery("lottery1", "CampA")
            )
        )

        Assert.assertTrue(
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

        Assert.assertEquals(1, user.joinedLotteries.size)

        Assert.assertEquals(
            "lottery2",
            user.joinedLotteries.first().lotteryId
        )
    }
}