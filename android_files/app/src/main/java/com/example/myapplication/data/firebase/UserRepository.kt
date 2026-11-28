package com.example.myapplication.data.firebase

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import android.content.Context

import android.provider.Settings.Secure
import androidx.compose.runtime.mutableStateOf
import com.example.myapplication.features.campsite.Campsite
import com.example.myapplication.features.lottery.Lottery
import com.example.myapplication.features.profile.AuthState
import com.example.myapplication.features.profile.ProfileDeletionPlan
import com.example.myapplication.features.profile.User
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * UserRepository contains a list of users, populated from Firestore, and several methods for
 * user objects.
 *
 * UserRepository populates the users list from Firestore and continues to listen for
 * updates. UserRepository has add, delete, update and find methods for users.
 */
class UserRepository(private val currentDeviceID: String, private val db: FirebaseFirestore = Firebase.firestore) {

    private val _users = mutableStateListOf<User>()

    private val _currentUser = mutableStateOf<User?>(null)
    private val userDataBase = db.collection("users")
     var authState = MutableStateFlow<AuthState>(AuthState.Loading)



    /**
     * List of users currently in Firestore, updated by a snapshot listener.
     */
    val users: List<User>
        get() = _users

    val currentUser: User?
        get() = _currentUser.value

    /**
     * Reads this device's already-loaded profile. All supported roles use the same
     * joined membership; owning or administering a lottery does not mean joining it.
     * LotteryRepository maintains joinedLotteries in its join/leave transactions.
     * Resolve IDs against live lottery records without another Firestore read.
     */
    fun getJoinedLotteries(lotteries: Map<String, Lottery>): List<Lottery> {
        val user = currentUser ?: return emptyList()
        if (user.userType.trim().lowercase() !in setOf("entrant", "admin", "campsite owner")) return emptyList()
        return user.joinedLotteries.mapNotNull { lotteries[it.lotteryId] }
            .distinctBy { it.id }
            .sortedByDescending { it.period.registrationStart }
    }

    /** Admin-only overview, including each active lottery's campsites and current member profiles. */
    fun getAllActiveLotteryWaitlists(lotteries: Map<String, Lottery>): List<Lottery> {
        if (currentUser?.userType?.trim()?.equals("admin", true) != true) return emptyList()
        val profiles = users.associateBy { it.deviceID }
        return lotteries.values.filterNot { it.drawn }.distinctBy { it.id }
            .sortedByDescending { it.period.registrationStart }
            .map { lottery -> lottery.copy(waitlist = lottery.waitlist.keys.associateWith { profiles[it] }) }
    }

    /**
     * Resolves waitlist IDs using the current user collection after checking access.
     * Missing profiles remain null so membership counts stay accurate. Embedded
     * profile copies are never used as a fallback for removed or changed users.
     */
    fun getLotteryWaitlist(lottery: Lottery?, campsites: Map<String, Campsite>): Lottery? {
        if (lottery == null || !LotteryRepository.canViewLotteryWaitlist(currentUser, lottery, campsites)) return null
        val profiles = users.associateBy { it.deviceID }
        return lottery.copy(waitlist = lottery.waitlist.keys.associateWith { profiles[it] })
    }

    /**
     * Adds a user to Firestore.
     *
     * @param newUser The user to save. [User.deviceID] is used as the document ID.
     */
    fun addUser(newUser: User) {
        userDataBase.document(newUser.deviceID).set(newUser)
    }

    /**
     * Deletes a profile and applies related cleanup through [ProfileDeletionPlan].
     *
     * Discovers records on the server and re-reads them in a transaction. Reads the
     * pending result, notification, and invitation paths for the user's lotteries
     * even if those records do not exist yet, so a concurrent draw or delivery can cause a retry.
     * Commits cleanup and profile deletion together.
     *
     * The transaction limit is applied to affected records, including derived paths.
     * Legacy relationship arrays still require discovery reads. Records newly linked
     * after discovery may require a fresh attempt or server-side cleanup.
     *
     * On success, removes the deleted user from the local list. Clears the current
     * profile and authentication state only when deleting this device's profile;
     * deleting another user does not log out the current administrator. Failure
     * does not explicitly clear local profile state.
     *
     * @param user Profile to delete, identified by [User.deviceID].
     * @return Task that succeeds after cleanup commits, or fails for an invalid ID,
     * failed server access, the document limit, or transaction errors.
     */
    fun deleteUser(user: User): Task<Void> {
        val userId = user.deviceID
        if (userId.isBlank() || '/' in userId) {
            return Tasks.forException(IllegalArgumentException("Invalid profile ID"))
        }
        val limitError = "Profile cleanup exceeds the client transaction limit. Server cleanup is required."
        val messages = db.collection("messages")
        val reads = listOf(
            userDataBase,
            db.collection("campsites"),
            db.collection("bookingSchedules"),
            db.collection("reservations"),
            db.collectionGroup("lottery"),
            db.collection("notifications").whereEqualTo("userId", userId),
            messages.whereEqualTo("senderId", userId),
            messages.whereEqualTo("recipientId", userId),
            db.collection("lotteryDrawResults").whereArrayContains("remainingEntrantIds", userId)
        ).map { it.get(Source.SERVER) }
        return Tasks.whenAllSuccess<QuerySnapshot>(reads)
            .onSuccessTask { snapshots ->
                val discovered = snapshots.flatMap { it.documents }.associateBy { it.reference.path }
                val fields = discovered.mapValues { (_, snapshot) -> snapshot.data.orEmpty() }
                val target = userDataBase.document(userId)
                val scope = ProfileDeletionPlan.discover(userId, fields)
                val refs = (scope.paths - target.path).associateWith { discovered[it]?.reference ?: db.document(it) } +
                    (target.path to target)
                check(refs.size <= 450) { limitError }
                db.runTransaction<Void> { transaction ->
                    val docs = refs.values.map { transaction.get(it) }
                        .filter { it.exists() }.associateTo(mutableMapOf()) { it.reference.path to it.data.orEmpty() }

                    // A join or leave writes the profile and lottery copies together. Restart
                    // discovery if those links or the affected lottery's identity have moved.
                    val profileFields = listOf("joinedLotteries", "campsiteIds")
                    check(profileFields.all { docs[target.path]?.get(it) == fields[target.path]?.get(it) } &&
                        ProfileDeletionPlan.structuralRelationshipsMatch(scope.structuralLotteryPaths, fields, docs)) {
                        "Profile relationships changed during cleanup. Retry profile deletion."
                    }

                    val queriedDrawIds = refs.keys.filter { it.startsWith("lotteryDrawResults/") }
                        .map { it.substringAfter('/') }
                    val affectedDrawIds = ProfileDeletionPlan.participatingLotteryIds(userId, fields) +
                        ProfileDeletionPlan.participatingLotteryIds(userId, docs) + queriedDrawIds
                    val additionalPaths = affectedDrawIds.flatMap { id ->
                        val recipientDocumentId = "${id}_$userId"
                        listOf("lotteryDrawResults/$id", "notifications/$recipientDocumentId", "reservations/$recipientDocumentId")
                    }.filterNot { it in refs }
                    check(refs.size + additionalPaths.size <= 450) { limitError }
                    for (path in additionalPaths) {
                        val document = transaction.get(db.document(path))
                        if (document.exists()) docs[path] = document.data.orEmpty()
                    }
                    ProfileDeletionPlan.build(userId, docs, System.currentTimeMillis()).forEach { (path, fields) ->
                        if (fields == null) transaction.delete(db.document(path))
                        else transaction.update(db.document(path), fields)
                    }
                    null
                }
            }.addOnSuccessListener {
                _users.removeAll { it.deviceID == userId }
                if (currentDeviceID == userId) {
                    _currentUser.value = null
                    authState.value = AuthState.NotRegistered
                }
            }
    }

    /**
     * Stops listening for user changes in Firestore.
     *
     * Call when this repository is no longer used. This releases the listener without
     * deleting user data, clearing the last loaded state, or cancelling pending writes.
     */
    fun close() = userListener.remove()

    /**
     * Updates editable profile fields on the existing document. Membership and ownership
     * fields belong to their respective transactional writers.
     */
    fun updateUser(newUser: User, oldUser: User): Task<Void> {
        val fields = buildMap<String, Any> {
            if (newUser.name != oldUser.name) put("name", newUser.name)
            if (newUser.email != oldUser.email) put("email", newUser.email)
            if (newUser.phoneNumber != oldUser.phoneNumber) put("phoneNumber", newUser.phoneNumber)
            if (newUser.lotteryAlertsEnabled != oldUser.lotteryAlertsEnabled) {
                put("lotteryAlertsEnabled", newUser.lotteryAlertsEnabled)
            }
        }
        return if (fields.isEmpty()) Tasks.forResult(null)
        else userDataBase.document(oldUser.deviceID).update(fields)
    }

    /**
     * Fetches a user document from _users.
     *
     * @param deviceID The unique device ID to identifier the user object by.
     * @return If a user with the specified Android ID exists, that [User] object is returned.
     * Otherwise, null is returned.
     */
    fun findUser(deviceID: String): User? {
        users.forEach { user ->
            if (user.deviceID == deviceID){
                return user
            }
        }

        return null
    }

    /**
     * Gets a user's device ID.
     *
     * @param context User's current device context. Stores unique Android ID.
     * @return Returns the user's Android ID as a string.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceID(context: Context): String {
        return Secure.getString(
            context.contentResolver,
            Secure.ANDROID_ID
        )
    }

    /**
     * Fetches the UserRepositories AuthState value
     *
     * @return Returns the UserRepositories AuthState value
     */

    private val userListener = userDataBase.addSnapshotListener(MetadataChanges.INCLUDE) { snapshots, error ->
        if (error != null) {
            authState.value = AuthState.Error
            return@addSnapshotListener
        }

        // Cached profiles may have been deleted on another device. Authenticate only
        // after a server snapshot, including when an empty server result matches cache.
        if (snapshots == null || snapshots.metadata.isFromCache) return@addSnapshotListener
        _users.clear()
        _currentUser.value = null

        snapshots.documents.forEach { document ->
            val user = try {
                document.toObject(User::class.java)
            } catch (e: Exception) {
                Log.e("UserRepository", "Skipping doc ${document.id}: ${e.message}", e)
                null
            }
            if (user != null) {
                _users.add(user)
                if (user.deviceID == currentDeviceID) {
                    _currentUser.value = user
                }
            }
        }

        if (_currentUser.value != null) {
            authState.value = AuthState.LoggedIn(_currentUser.value!!)
        }

        else {
            authState.value = AuthState.NotRegistered
        }
    }
}
