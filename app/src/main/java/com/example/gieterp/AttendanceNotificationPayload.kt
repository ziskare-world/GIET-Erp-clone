package com.example.gieterp

data class AttendanceNotificationPayload(
    val eventId: String,
    val rollNo: String,
    val subject: String,
    val status: String,
    val attendanceDate: String,
    val semester: Int?,
    val updatedAt: String,
) {
    fun matchesRollNo(activeRollNo: String?): Boolean {
        return !activeRollNo.isNullOrBlank() && activeRollNo.equals(rollNo, ignoreCase = true)
    }

    companion object {
        private const val TYPE_ATTENDANCE_UPDATE = "attendance_update"

        fun fromData(data: Map<String, String>): AttendanceNotificationPayload? {
            if (data.isEmpty()) {
                return null
            }

            val type = data.valueForKeys("type", "notificationType", "notification_type")
            if (type.isNotBlank() && !type.equals(TYPE_ATTENDANCE_UPDATE, ignoreCase = true)) {
                return null
            }

            val eventId = data.valueForKeys("eventId", "event_id").trim()
            val rollNo = data.valueForKeys("rollNo", "roll_no", "vchRollNo").trim()
            val status = normalizeStatus(
                data.valueForKeys("status", "attendanceStatus", "attendance_status"),
            )
            if (eventId.isBlank() || rollNo.isBlank() || status.isBlank()) {
                return null
            }

            return AttendanceNotificationPayload(
                eventId = eventId,
                rollNo = rollNo,
                subject = data.valueForKeys("subject", "subjectName", "subject_name").trim(),
                status = status,
                attendanceDate = data.valueForKeys("attendanceDate", "attendance_date", "date").trim(),
                semester = data.intForKeys("semester", "intSemester", "int_semester"),
                updatedAt = data.valueForKeys("updatedAt", "updated_at").trim(),
            )
        }

        internal fun normalizeStatus(rawStatus: String): String {
            return when (rawStatus.trim().uppercase()) {
                "P", "PRESENT", "PRESENCE" -> "Present"
                "A", "ABSENT", "AB" -> "Absent"
                else -> rawStatus.trim().replaceFirstChar { character ->
                    if (character.isLowerCase()) character.titlecase() else character.toString()
                }
            }
        }

        private fun Map<String, String>.valueForKeys(vararg keys: String): String {
            keys.forEach { key ->
                val value = this[key]?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    return value
                }
            }
            return ""
        }

        private fun Map<String, String>.intForKeys(vararg keys: String): Int? {
            keys.forEach { key ->
                val value = this[key]?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    return value.toIntOrNull()
                }
            }
            return null
        }
    }
}
