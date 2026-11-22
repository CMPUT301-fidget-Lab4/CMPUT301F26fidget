package com.example.myapplication.backEnd.featureModels

/**
 * Result notifications for 1 entrant. Stored at notifications/{lotteryId_{userId}.
 * Every field has a default so Firestore can rebuild it with toObject().
 */
data class AppNotification(
    var id: String = "",
    var userId: String = "",      // the entrant's deviceID
    var lotteryId: String = "",
    var type: String = "",        // "WIN" or "LOSE"
    var message: String = "",
    var read: Boolean = false,
    var createdAt: Long = System.currentTimeMillis()
)

/** Builds one WIN notification per winner and one LOSE per remaining entrant. */
fun buildResultNotifications(
    lotteryId: String,
    campsiteName: String,
    waitlist: List<String>,
    winners: List<String>
): List<AppNotification> {
    val winnerSet = winners.toSet()
    val losers = waitlist.distinct().filterNot { it in winnerSet }
    fun make(userId: String, type: String, message: String) = AppNotification(
        id = "${lotteryId}_$userId", userId = userId,
        lotteryId = lotteryId, type = type, message = message
    )
    return winners.map { make(it, "WIN", "You won the lottery for $campsiteName! Check your invitations.") } +
            losers.map { make(it, "LOSE", "You weren't selected in the lottery for $campsiteName.") }
}