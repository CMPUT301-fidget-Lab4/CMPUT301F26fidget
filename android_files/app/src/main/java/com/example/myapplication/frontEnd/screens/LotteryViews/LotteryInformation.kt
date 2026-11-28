package com.example.myapplication.frontEnd.screens.LotteryViews

import com.example.myapplication.features.lottery.Lottery
import java.time.LocalDateTime

/** Formats membership count against capacity; zero capacity means unlimited, not full. */
internal fun waitlistSummary(lottery: Lottery): String {
    val count = lottery.waitlist.size
    return if (lottery.waitlistCapacity > 0) {
        "Waitlist: $count / ${lottery.waitlistCapacity} joined" +
            if (count >= lottery.waitlistCapacity) " (full)" else ""
    } else "Waitlist: $count joined (no limit)"
}

/** Registration status is independent of the capacity count and the parent campsite's active flag. */
internal fun lotteryRegistrationStatus(lottery: Lottery, now: LocalDateTime): String = when {
    lottery.drawn -> "Lottery drawn"
    now.isBefore(lottery.period.registrationStart) -> "Registration has not opened"
    !now.isBefore(lottery.period.registrationEnd) -> "Registration closed"
    else -> "Registration open"
}
