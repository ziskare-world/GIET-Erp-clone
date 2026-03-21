package com.example.gieterp

import android.content.Intent

object SpecialStudentContract {
    const val EXTRA_STUDENT_ROLL_NO = "specialStudentRollNo"
    const val EXTRA_STUDENT_REGISTRATION_NO = "specialStudentRegistrationNo"
    const val EXTRA_STUDENT_NAME = "specialStudentName"
    const val EXTRA_STUDENT_PROGRAM = "specialStudentProgram"
    const val EXTRA_STUDENT_BRANCH = "specialStudentBranch"
    const val EXTRA_STUDENT_SEMESTER = "specialStudentSemester"
    const val EXTRA_STUDENT_SECTION = "specialStudentSection"
    const val EXTRA_STUDENT_CONTACT_NO = "specialStudentContactNo"
    const val EXTRA_STUDENT_HOSTEL_INFO = "specialStudentHostelInfo"

    fun putStudentExtras(intent: Intent, student: SpecialStudent): Intent = intent.apply {
        putExtra(EXTRA_STUDENT_ROLL_NO, student.rollNo)
        putExtra(EXTRA_STUDENT_REGISTRATION_NO, student.registrationNo)
        putExtra(EXTRA_STUDENT_NAME, student.studentName)
        putExtra(EXTRA_STUDENT_PROGRAM, student.program)
        putExtra(EXTRA_STUDENT_BRANCH, student.branch)
        putExtra(EXTRA_STUDENT_SEMESTER, student.semester)
        putExtra(EXTRA_STUDENT_SECTION, student.section)
        putExtra(EXTRA_STUDENT_CONTACT_NO, student.contactNo)
        putExtra(EXTRA_STUDENT_HOSTEL_INFO, student.hostelInfo)
    }

    fun readStudent(intent: Intent): SpecialStudent? {
        val rollNo = intent.getStringExtra(EXTRA_STUDENT_ROLL_NO)?.takeIf { it.isNotBlank() } ?: return null
        return SpecialStudent(
            rollNo = rollNo,
            registrationNo = intent.getStringExtra(EXTRA_STUDENT_REGISTRATION_NO).orEmpty(),
            studentName = intent.getStringExtra(EXTRA_STUDENT_NAME).orEmpty(),
            program = intent.getStringExtra(EXTRA_STUDENT_PROGRAM).orEmpty(),
            branch = intent.getStringExtra(EXTRA_STUDENT_BRANCH).orEmpty(),
            semester = intent.getIntExtra(EXTRA_STUDENT_SEMESTER, 1).coerceAtLeast(1),
            section = intent.getStringExtra(EXTRA_STUDENT_SECTION).orEmpty(),
            contactNo = intent.getStringExtra(EXTRA_STUDENT_CONTACT_NO).orEmpty(),
            hostelInfo = intent.getStringExtra(EXTRA_STUDENT_HOSTEL_INFO).orEmpty(),
        )
    }
}
