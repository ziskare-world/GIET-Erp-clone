package com.example.gieterp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttendanceNotificationPayloadTest {
    @Test
    fun `fromData parses attendance payload`() {
        val payload = AttendanceNotificationPayload.fromData(
            mapOf(
                "type" to "attendance_update",
                "eventId" to "evt-001",
                "rollNo" to "23CSE476",
                "subject" to "DBMS",
                "status" to "P",
                "semester" to "6",
            ),
        )

        requireNotNull(payload)
        assertEquals("evt-001", payload.eventId)
        assertEquals("23CSE476", payload.rollNo)
        assertEquals("DBMS", payload.subject)
        assertEquals("Present", payload.status)
        assertEquals(6, payload.semester)
    }

    @Test
    fun `fromData ignores unsupported notification types`() {
        val payload = AttendanceNotificationPayload.fromData(
            mapOf(
                "type" to "marks_update",
                "eventId" to "evt-002",
                "rollNo" to "23CSE476",
                "status" to "Absent",
            ),
        )

        assertNull(payload)
    }

    @Test
    fun `normalizeStatus formats absent correctly`() {
        assertEquals("Absent", AttendanceNotificationPayload.normalizeStatus("a"))
        assertEquals("Absent", AttendanceNotificationPayload.normalizeStatus("AB"))
    }
}
