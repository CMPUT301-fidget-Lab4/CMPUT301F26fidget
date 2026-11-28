package com.example.myapplication.core.datetime

import java.time.ZoneOffset

/**
 * Alberta uses UTC-06:00 year-round from November 2026 onward.
 * Use the explicit offset because older Android time-zone databases still apply
 * the former winter clock change to America/Edmonton.
 * Reference: https://www.alberta.ca/albertas-new-time-system-abt
 * This setting is for current and future app dates, not historical conversions.
 */
object AlbertaTime {
    val zone: ZoneOffset = ZoneOffset.ofHours(-6)
}
