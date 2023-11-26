package com.topunion.camera

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime


class Util {
    @Suppress("unused")
    companion object {
        fun timeFormatSafe(t: Instant): String {
            val zdt: ZonedDateTime = t.atZone(UTC)
            return "%04d%02d%02d_%02d%02d%02d".format(
                zdt.year,
                zdt.month.value,
                zdt.dayOfMonth,
                zdt.hour,
                zdt.minute,
                zdt.second
            )
        }

        fun timeFormatRFC3339(t: Instant): String {
            val zdt: ZonedDateTime = t.atZone(UTC)
            return "%04d-%02d-%02dT%02d:%02d:%02dZ".format(
                zdt.year,
                zdt.month.value,
                zdt.dayOfMonth,
                zdt.hour,
                zdt.minute,
                zdt.second
            )
        }

        fun timeDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, nanoSec: Int, location: ZoneId) : Instant {
            val zdt = ZonedDateTime.of(year, month, day, hour, minute, second, nanoSec, location)
            return zdt.toInstant()
        }

        val UTC: ZoneId = ZoneId.of("UTC")
        const val January = 1
        const val February = 2
        const val March = 3
        const val April = 4
        const val May = 5
        const val June = 6
        const val July = 7
        const val August = 8
        const val September = 9
        const val October = 10
        const val November = 11
        const val December = 12

        fun removeAll(fpath: String) {
            removeAllF(File(fpath))
        }
        private fun removeAllF(fileOrDirectory: File) {
            if (fileOrDirectory.isDirectory) {
                for (child in fileOrDirectory.listFiles()!!) {
                    removeAllF(child)
                }
            }
            fileOrDirectory.delete()
        }
    }
}