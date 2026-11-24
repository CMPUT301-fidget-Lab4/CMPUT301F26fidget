package com.example.myapplication.frontEnd.screens.MessageViews

import com.example.myapplication.backEnd.time.AlbertaTime
import com.example.myapplication.backEnd.userModels.User
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val timeOnly = DateTimeFormatter.ofPattern("h:mm a")
private val dateAndTime = DateTimeFormatter.ofPattern("MMM d, h:mm a")

/**
 * A user's name for showing in the message screens.
 *
 * @param userId The user's device ID.
 * @param findUser Looks up a user by device ID.
 * @return Their name, or "Unknown user" if they can't be found (for example a deleted profile).
 */
internal fun displayName(userId: String, findUser: (String) -> User?): String =
    findUser(userId)?.name?.ifBlank { null } ?: "Unknown user"

/**
 * Formats when a message was sent: just the time if it was today,
 * otherwise the date and time.
 *
 * @param sentAt When the message was sent, in milliseconds.
 */
internal fun formatMessageTime(sentAt: Long): String {
    val sent = Instant.ofEpochMilli(sentAt).atZone(AlbertaTime.zone)
    val today = LocalDate.now(AlbertaTime.zone)
    return if (sent.toLocalDate() == today) sent.format(timeOnly) else sent.format(dateAndTime)
}