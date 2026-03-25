package com.example.gieterp

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

private const val ATTENDANCE_THRESHOLD = 0.8
private const val MIN_SEMESTER = 1
private const val MAX_SEMESTER = 8
private const val FLOATING_POINT_EPSILON = 1e-9

data class AttendanceRecord(
    val attendedClasses: Double,
    val totalClasses: Double,
    val subjectLabel: String,
)

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

    fun extractAttendanceRecord(row: JSONObject): AttendanceRecord? {
        val counts = extractAttendanceCount(
            totalValue = row.optString("Total", ""),
            attendedValue = row.optString("Attended", ""),
        ) ?: return null

        return AttendanceRecord(
            attendedClasses = counts.first,
            totalClasses = counts.second,
            subjectLabel = extractSubjectLabel(row),
        )
    }

    fun extractAttendanceColumnRecords(dataArray: JSONArray): List<AttendanceRecord> {
        val rows = buildList<Map<String, String>> {
            for (rowIndex in 0 until dataArray.length()) {
                val row = dataArray.optJSONObject(rowIndex) ?: continue
                val values = linkedMapOf<String, String>()
                val keys = row.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    values[key] = row.optString(key, "")
                }
                add(values)
            }
        }
        return extractAttendanceColumnRecords(rows)
    }

    fun extractAttendanceColumnRecords(rows: Iterable<Map<String, String>>): List<AttendanceRecord> {
        val subjectTotals = linkedMapOf<String, Pair<Double, Double>>()

        rows.forEach { row ->
            row.forEach { (key, value) ->
                if (key.equals("AttendanceDate", ignoreCase = true)) {
                    return@forEach
                }

                val counts = extractAttendanceCount(
                    totalValue = value,
                    attendedValue = "",
                ) ?: return@forEach

                val subjectLabel = key.trim().ifBlank { "Unknown Subject" }
                val existing = subjectTotals[subjectLabel]
                val attended = (existing?.first ?: 0.0) + counts.first
                val total = (existing?.second ?: 0.0) + counts.second
                subjectTotals[subjectLabel] = attended to total
            }
        }

        return subjectTotals.map { (subjectLabel, counts) ->
            AttendanceRecord(
                attendedClasses = counts.first,
                totalClasses = counts.second,
                subjectLabel = subjectLabel,
            )
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

    private fun extractSubjectLabel(row: JSONObject): String {
        val preferredKeys = listOf(
            "Subject",
            "subject",
            "SubjectName",
            "subjectName",
            "vchSubjectName",
            "vchSubjectShortName",
            "PaperName",
            "paperName",
            "CourseName",
            "courseName",
        )

        preferredKeys.forEach { key ->
            row.optString(key).trim().takeIf { it.isNotBlank() }?.let { return it }
        }

        val keys = row.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.contains("subject", ignoreCase = true) || key.contains("paper", ignoreCase = true) || key.contains("course", ignoreCase = true)) {
                row.optString(key).trim().takeIf { it.isNotBlank() }?.let { return it }
            }
        }

        return ""
    }
}
