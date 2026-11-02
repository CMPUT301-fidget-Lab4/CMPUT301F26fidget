package com.example.myapplication.features


import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import java.time.LocalDate

class LotteryRepository {

    private val db = Firebase.firestore
    private val campsiteRef = db.collection("campsites")

    /**
     * Gets the Firestore location of a campsite's lottery document.
     *
     * Points to campsites/<campsiteName>/lottery/current. This only builds the
     * location, it does not read or write any data.
     *
     * @param campsiteName the name of the campsite, which is also its document id
     */
    private fun lotteryDoc(campsiteName: String) =
        campsiteRef.document(campsiteName).collection("lottery").document("current")



    /**
     * Adds an entrant to a lottery's waitlist and saves it.
     *
     * @param campsiteName Any campsite participating in the lottery.
     * @param user The entrant joining the lottery.
     * @param onResult Returns true if joining and saving succeeded.
     */
    fun joinLotteryWaitlist(
        campsiteName: String,
        user: User,
        onResult: (Boolean) -> Unit
    ) {
        getLottery(campsiteName) { lottery ->

            if (lottery == null) {
                onResult(false)
                return@getLottery
            }

            // Check registration dates, role, duplicates and capacity.
            if (!lottery.joinWaitlist(user)) {
                onResult(false)
                return@getLottery
            }

            // Your saveLottery updates all participating campsites.
            saveLottery(campsiteName, lottery)
                .addOnSuccessListener {
                    onResult(true)
                }
                .addOnFailureListener {
                    onResult(false)
                }
        }
    }




    /**
     * Saves the lottery to all participating campsites.
     *
     * Also marks each campsite as being in a lottery
     * and sets its registration and booking flags.
     *
     * @param campsiteName The main campsite name.
     * @param lottery The lottery being saved.
     * @return Task indicating whether all changes succeeded.
     */
    fun saveLottery(
        campsiteName: String,
        lottery: Lottery
    ): Task<Void> {

        val data = hashMapOf(
            "id" to lottery.id,
            "campsiteIds" to lottery.campsiteIds,
            "numWinners" to lottery.numWinners,
            "waitlistCapacity" to lottery.waitlistCapacity,
            "waitlist" to lottery.waitlist,
            "winners" to lottery.winners,
            "drawn" to lottery.drawn,
            "registrationStart" to lottery.period.registrationStart.toString(),
            "registrationEnd" to lottery.period.registrationEnd.toString(),
            "bookingStart" to lottery.period.bookingStart.toString(),
            "bookingEnd" to lottery.period.bookingEnd.toString()
        )

        // Create one batch for all database changes
        val batch = db.batch()

        // Find every campsite participating in the lottery
        val ids = (lottery.campsiteIds + campsiteName).distinct()

        for (id in ids) {

            val campsiteDoc = campsiteRef.document(id)

            // Save lottery information under each campsite
            batch.set(
                lotteryDoc(id),
                data
            )

            // Mark each campsite as participating
            batch.update(
                campsiteDoc,
                mapOf(
                    "inLottery" to true,
                    "reservationSet" to true,
                    "bookingSet" to true
                )
            )
        }

        // Save all changes together
        return batch.commit()
    }


    /**
     * Loads the lottery saved for a campsite from Firestore.
     *
     * The lottery is read from campsites/<campsiteName>/lottery/current. The date
     * fields are stored as text (like "2026-11-01") and are converted back into
     * LocalDate objects.
     *
     * The data loads in the background, so the result is not returned directly.
     * It is passed to [onResult] when it is ready.
     *
     * @param campsiteName the name of the campsite, which is also its document id
     * @param onResult called with the loaded [Lottery], or with null if the
     * campsite has no saved lottery or the read fails
     */
    fun getLottery(campsiteName: String, onResult: (Lottery?) -> Unit) {
        lotteryDoc(campsiteName).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onResult(null)
                    return@addOnSuccessListener
                }

                val period = Reservation(
                    readDate(doc, "registrationStart"),
                    readDate(doc, "registrationEnd"),
                    readDate(doc, "bookingStart"),
                    readDate(doc, "bookingEnd")
                )

                val lottery = Lottery(
                    id = doc.getString("id") ?: "",
                    campsiteIds = (doc.get("campsiteIds") as? List<String>) ?: listOf(),
                    period = period,
                    numWinners = doc.getLong("numWinners")?.toInt() ?: 1,
                    waitlistCapacity = doc.getLong("waitlistCapacity")?.toInt() ?: 0,
                    waitlist = (doc.get("waitlist") as? List<String>) ?: listOf(),
                    winners = (doc.get("winners") as? List<String>) ?: listOf(),
                    drawn = doc.getBoolean("drawn") ?: false
                )
                onResult(lottery)
            }
            .addOnFailureListener {
                onResult(null)
            }
    }


    /**
     * Deletes a lottery from every campsite participating in it.
     * Also resets the campsite flags to false.
     *
     * @param campsiteName Any campsite belonging to the lottery.
     * @return Task indicating whether the deletion succeeded.
     */
    fun deleteLottery(campsiteName: String): Task<Void> {

        // Read the lottery to find all participating campsites.
        return lotteryDoc(campsiteName).get().continueWithTask { task ->

            val doc = task.result
                ?: throw IllegalStateException("Could not load lottery.")

            if (!doc.exists()) {
                throw IllegalStateException("Lottery does not exist.")
            }

            // Get the names of every campsite in this lottery.
            val ids = (
                    (doc.get("campsiteIds") as? List<*>)
                        ?.filterIsInstance<String>()
                        .orEmpty() + campsiteName
                    ).filter { it.isNotBlank() }.distinct()

            val batch = db.batch()

            for (id in ids) {

                val campsiteDoc = campsiteRef.document(id)

                // Delete this campsite's copy of the lottery.
                batch.delete(
                    campsiteDoc.collection("lottery").document("current")
                )

                // Reset its three flags.
                batch.update(
                    campsiteDoc,
                    mapOf(
                        "inLottery" to false,
                        "reservationSet" to false,
                        "bookingSet" to false
                    )
                )
            }

            // Apply all deletions and updates together.
            batch.commit()
        }
    }


    private fun readDate(doc: DocumentSnapshot, field: String): LocalDate {
        val text = doc.getString(field) ?: return LocalDate.now()
        return LocalDate.parse(text)
    }
}