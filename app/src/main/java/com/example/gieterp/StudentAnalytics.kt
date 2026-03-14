package com.example.gieterp

import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

private const val ATTENDANCE_THRESHOLD = 0.8
private const val MIN_SEMESTER = 1
private const val MAX_SEMESTER = 8
private const val FLOATING_POINT_EPSILON = 1e-9

data class AttendanceSummary(
    val attendedClasses: Double,
    val totalClasses: Double,
) {
    private val thresholdTargetClasses: Double
        get() = ATTENDANCE_THRESHOLD * totalClasses

    val percentage: Double
        get() = if (totalClasses == 0.0) 0.0 else (attendedClasses / totalClasses) * 100

    val isAtOrAboveThreshold: Boolean
        get() = (attendedClasses - thresholdTargetClasses) >= -FLOATING_POINT_EPSILON

    val classesCanBunk: Int
        get() = if (!isAtOrAboveThreshold) {
            0
        } else {
            floor((((attendedClasses / ATTENDANCE_THRESHOLD) - totalClasses) + FLOATING_POINT_EPSILON).coerceAtLeast(0.0)).toInt()
        }

    val classesNeededForMinimum: Int
        get() = if (isAtOrAboveThreshold) {
            0
        } else {
            ceil(
                (((thresholdTargetClasses - attendedClasses) / (1 - ATTENDANCE_THRESHOLD)) - FLOATING_POINT_EPSILON)
                    .coerceAtLeast(0.0)
            ).toInt()
        }

    fun percentageLabel(): String = String.format(Locale.US, "%.2f%%", percentage)

    fun statusLabel(): String {
        return if (isAtOrAboveThreshold) {
            "Bunk: $classesCanBunk Classes"
        } else {
            "Attend $classesNeededForMinimum classes to reach 80%"
        }
    }
}

object StudentAnalytics {
    fun calculateCurrentSemester(
        rollNo: String,
        calendar: Calendar = Calendar.getInstance(),
    ): Int {
        val admissionYearPrefix = rollNo.take(2).toIntOrNull() ?: return MIN_SEMESTER
        val admissionYear = 2000 + admissionYearPrefix
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val yearDiff = (currentYear - admissionYear).coerceAtLeast(0)
        val semester = yearDiff * 2 + if (currentMonth >= 7) 1 else 0
        return semester.coerceIn(MIN_SEMESTER, MAX_SEMESTER)
    }

    fun extractAttendanceCount(totalValue: String, attendedValue: String): Pair<Double, Double>? {
        if (totalValue.contains('/')) {
            val parts = totalValue.split('/', limit = 2)
            val attended = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
            val total = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
            if (attended != null && total != null && total > 0) {
                return attended to total
            }
        }

        val attended = attendedValue.trim().toDoubleOrNull()
        val total = totalValue.trim().toDoubleOrNull()
        return if (attended != null && total != null && total > 0) {
            attended to total
        } else {
            null
        }
    }

    fun summarizeAttendance(records: Iterable<Pair<Double, Double>>): AttendanceSummary? {
        var totalAttended = 0.0
        var totalClasses = 0.0

        for ((attended, total) in records) {
            if (total > 0) {
                totalAttended += attended
                totalClasses += total
            }
        }

        return if (totalClasses > 0) {
            AttendanceSummary(totalAttended, totalClasses)
        } else {
            null
        }
    }
}
