package com.example.myapplication.features


import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import com.google.firebase.Timestamp

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

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
     * Draws winners after registration closes.
     *
     * Saves the winning entrants to every campsite
     * participating in the lottery.
     *
     * @param campsiteName Any campsite in the lottery.
     * @param onResult The selected winner IDs, or null on failure.
     */
    fun drawLotteryWinners(
        campsiteName: String,
        onResult: (List<String>?) -> Unit
    ) {
        getLottery(campsiteName) { lottery ->

            if (lottery == null || !lottery.drawWinners()) {
                onResult(null)
                return@getLottery
            }

            // Save winners and drawn = true to every campsite.
            saveLottery(campsiteName, lottery)
                .addOnSuccessListener {
                    onResult(lottery.winners)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        }
    }




    /**
     * Adds an entrant to the waitlist and records
     * the joined lottery in the user's Firestore document.
     *
     * @param campsiteName The campsite the entrant selected.
     * @param user The entrant joining.
     * @param onResult true when both saves succeed.
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

            // Validate registration time, role,
            // duplicate entries, and waitlist capacity.
            if (!lottery.joinWaitlist(user)) {
                onResult(false)
                return@getLottery
            }

            // Save the updated waitlist to every campsite.
            saveLottery(campsiteName, lottery)
                .addOnSuccessListener {

                    val entry = mapOf(
                        "lotteryId" to lottery.id,
                        "campsiteName" to campsiteName
                    )

                    // Save the signup in the User document.
                    db.collection("users")
                        .document(user.deviceID)
                        .update(
                            "joinedLotteries",
                            com.google.firebase.firestore.FieldValue
                                .arrayUnion(entry)
                        )
                        .addOnSuccessListener {

                            // Update the local User object too.
                            user.addJoinedLottery(
                                lottery.id,
                                campsiteName
                            )

                            onResult(true)
                        }
                        .addOnFailureListener {
                            onResult(false)
                        }
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

        // Use the time zone where the campsite operates.
        // Replace this if your campsites use another time zone.
        val zone = ZoneId.of("America/Edmonton")

        val closingTime = Timestamp(
            lottery.period.registrationEnd
                .atZone(zone)
                .toEpochSecond(),
            lottery.period.registrationEnd.nano
        )

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
            // Used to automatically trigger the lottery draw.
            "registrationEndAt" to closingTime
        )

        val batch = db.batch()

        val ids = (
                lottery.campsiteIds + campsiteName
                ).filter { it.isNotBlank() }.distinct()

        val mainCampsite = lottery.campsiteIds.firstOrNull()
            ?: campsiteName

        for (id in ids) {

            val campsiteDoc = campsiteRef.document(id)

            // Mark only ONE copy as the primary lottery.
            // This prevents the scheduled function from
            // drawing the same lottery multiple times.
            val lotteryData = data.toMutableMap()

            lotteryData["isPrimary"] = id == mainCampsite

            batch.set(
                lotteryDoc(id),
                lotteryData
            )

            batch.update(
                campsiteDoc,
                mapOf(
                    "inLottery" to true,
                    "reservationSet" to true,
                    "bookingSet" to true
                )
            )
        }

        return batch.commit()
    }




    // Might not be needed because loading in campsites already loads in all the lotteries
    // because each campsite has a lottery.
    /**
     * Gets a lottery from Firestore using a campsite name.
     *
     * Loads its registration dates, booking dates, waitlist,
     * winners, and lottery status.
     *
     * @param campsiteName The campsite containing the lottery.
     * @param onResult The lottery, or null if it cannot be loaded.
     */
    fun getLottery(
        campsiteName: String,
        onResult: (Lottery?) -> Unit
    ) {
        if (campsiteName.isBlank()) {
            onResult(null)
            return
        }

        lotteryDoc(campsiteName).get()
            .addOnSuccessListener { doc ->

                if (!doc.exists()) {
                    onResult(null)
                    return@addOnSuccessListener
                }

                try {
                    val period = Registration(
                        registrationStart = readDate(doc, "registrationStart"),
                        registrationEnd = readDate(doc, "registrationEnd"),
                    )

                    val lottery = Lottery(
                        id = doc.getString("id") ?: "",
                        campsiteIds = (
                                doc.get("campsiteIds") as? List<*>
                                )?.filterIsInstance<String>() ?: emptyList(),
                        period = period,
                        numWinners = doc.getLong("numWinners")?.toInt() ?: 1,
                        waitlistCapacity = doc.getLong("waitlistCapacity")?.toInt() ?: 0,
                        waitlist = (
                                doc.get("waitlist") as? List<*>
                                )?.filterIsInstance<String>() ?: emptyList(),
                        winners = (
                                doc.get("winners") as? List<*>
                                )?.filterIsInstance<String>() ?: emptyList(),
                        drawn = doc.getBoolean("drawn") ?: false
                    )

                    onResult(lottery)

                } catch (e: Exception) {
                    onResult(null)
                }
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


    private fun readDate(
        doc: DocumentSnapshot,
        field: String
    ): LocalDateTime {

        val text = doc.getString(field)
            ?: throw IllegalArgumentException("Missing $field")

        return LocalDateTime.parse(text)
    }
}