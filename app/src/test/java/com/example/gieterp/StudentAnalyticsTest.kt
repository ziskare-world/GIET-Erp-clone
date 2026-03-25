package com.example.gieterp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class StudentAnalyticsTest {
    @Test
    fun `calculateCurrentSemester uses month aware semester boundaries`() {
        val marchCalendar = GregorianCalendar(2026, Calendar.MARCH, 12)
        val augustCalendar = GregorianCalendar(2026, Calendar.AUGUST, 12)

        assertEquals(4, StudentAnalytics.calculateCurrentSemester("24ABC123", marchCalendar))
        assertEquals(5, StudentAnalytics.calculateCurrentSemester("24ABC123", augustCalendar))
    }

    @Test
    fun `calculateCurrentSemester falls back safely for invalid roll numbers`() {
        assertEquals(1, StudentAnalytics.calculateCurrentSemester("A"))
    }

    @Test
    fun `extractAttendanceCount supports slash separated totals`() {
        assertEquals(8.0 to 10.0, StudentAnalytics.extractAttendanceCount("8/10", ""))
    }

    @Test
    fun `summarizeAttendance computes attendance actions correctly`() {
        val belowThreshold = StudentAnalytics.summarizeAttendance(listOf(15.0 to 20.0))
        val aboveThreshold = StudentAnalytics.summarizeAttendance(listOf(18.0 to 20.0))
        val exactThreshold = StudentAnalytics.summarizeAttendance(listOf(16.0 to 20.0))

        requireNotNull(belowThreshold)
        requireNotNull(aboveThreshold)
        requireNotNull(exactThreshold)

        assertEquals("75.00%", belowThreshold.percentageLabel())
        assertEquals(5, belowThreshold.classesNeededForMinimum)
        assertEquals("Attend 5 classes to reach 80%", belowThreshold.statusLabel())

        assertEquals(2, aboveThreshold.classesCanBunk)
        assertEquals("Bunk: 2 Classes", aboveThreshold.statusLabel())

        assertEquals("80.00%", exactThreshold.percentageLabel())
        assertEquals(0, exactThreshold.classesCanBunk)
        assertEquals(0, exactThreshold.classesNeededForMinimum)
        assertEquals("Bunk: 0 Classes", exactThreshold.statusLabel())
    }

    @Test
    fun `summarizeAttendance handles floating point threshold boundaries`() {
        val nearThresholdLow = StudentAnalytics.summarizeAttendance(listOf(7.999999 to 10.0))
        val nearThresholdHigh = StudentAnalytics.summarizeAttendance(listOf(8.000001 to 10.0))

        requireNotNull(nearThresholdLow)
        requireNotNull(nearThresholdHigh)

        assertEquals(1, nearThresholdLow.classesNeededForMinimum)
        assertEquals("Attend 1 classes to reach 80%", nearThresholdLow.statusLabel())

        assertEquals(0, nearThresholdHigh.classesCanBunk)
        assertEquals("Bunk: 0 Classes", nearThresholdHigh.statusLabel())
    }

    @Test
    fun `summarizeAttendance returns null when no valid classes exist`() {
        assertNull(StudentAnalytics.summarizeAttendance(emptyList()))
    }

    @Test
    fun `extractAttendanceColumnRecords uses report column names as subjects`() {
        val rows = listOf(
            mapOf(
                "AttendanceDate" to "2026-03-01",
                "MATH" to "1/1",
                "ENGLISH" to "0/1",
            ),
            mapOf(
                "AttendanceDate" to "2026-03-02",
                "MATH" to "1/1",
                "ENGLISH" to "1/1",
            ),
        )

        val records = StudentAnalytics.extractAttendanceColumnRecords(rows)
        val recordsBySubject = records.associateBy { it.subjectLabel }

        assertEquals(2, records.size)
        assertEquals(2.0, recordsBySubject.getValue("MATH").attendedClasses, 0.0)
        assertEquals(2.0, recordsBySubject.getValue("MATH").totalClasses, 0.0)
        assertEquals(1.0, recordsBySubject.getValue("ENGLISH").attendedClasses, 0.0)
        assertEquals(2.0, recordsBySubject.getValue("ENGLISH").totalClasses, 0.0)
    }
}
