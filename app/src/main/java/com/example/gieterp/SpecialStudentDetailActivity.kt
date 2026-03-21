package com.example.gieterp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class SpecialStudentDetailActivity : AppCompatActivity() {

    private lateinit var student: SpecialStudent
    private lateinit var backButton: Button
    private lateinit var studentNameText: TextView
    private lateinit var rollNoText: TextView
    private lateinit var registrationNoText: TextView
    private lateinit var programBranchText: TextView
    private lateinit var semesterSectionText: TextView
    private lateinit var contactText: TextView
    private lateinit var hostelInfoText: TextView
    private lateinit var semesterSpinner: Spinner
    private lateinit var attendanceButton: Button
    private lateinit var internalMarksButton: Button
    private lateinit var semesterResultsButton: Button
    private lateinit var restrictionText: TextView

    private var selectedSemester = 1
    private var maxSemester = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_special_student_detail)

        SystemBarInsets.apply(findViewById(R.id.main))

        student = SpecialStudentContract.readStudent(intent) ?: run {
            Toast.makeText(this, R.string.special_student_details_missing, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        maxSemester = intent.getIntExtra(AppSession.EXTRA_OVERRIDE_MAX_SEMESTER, student.semester).coerceIn(1, 8)
        val initialSemester = intent.getIntExtra(AppSession.EXTRA_OVERRIDE_INITIAL_SEMESTER, maxSemester)
        selectedSemester = initialSemester.coerceIn(1, maxSemester)

        bindViews()
        bindStudentProfile()
        setupSemesterSpinner()
        setupActions()
    }

    private fun bindViews() {
        backButton = findViewById(R.id.buttonBack)
        studentNameText = findViewById(R.id.studentNameText)
        rollNoText = findViewById(R.id.rollNoText)
        registrationNoText = findViewById(R.id.registrationNoText)
        programBranchText = findViewById(R.id.programBranchText)
        semesterSectionText = findViewById(R.id.semesterSectionText)
        contactText = findViewById(R.id.contactText)
        hostelInfoText = findViewById(R.id.hostelInfoText)
        semesterSpinner = findViewById(R.id.semesterSpinner)
        attendanceButton = findViewById(R.id.openAttendanceButton)
        internalMarksButton = findViewById(R.id.openInternalMarksButton)
        semesterResultsButton = findViewById(R.id.openSemesterResultsButton)
        restrictionText = findViewById(R.id.restrictionText)

        backButton.setOnClickListener { finish() }
    }

    private fun bindStudentProfile() {
        val fallback = getString(R.string.common_not_available)
        studentNameText.text = student.studentName.ifBlank { fallback }
        rollNoText.text = getString(R.string.label_roll_no, student.rollNo)
        registrationNoText.text = getString(R.string.label_registration_no, student.registrationNo.ifBlank { fallback })
        programBranchText.text = getString(
            R.string.label_program_branch,
            student.program.ifBlank { fallback },
            student.branch.ifBlank { fallback },
        )
        semesterSectionText.text = getString(
            R.string.label_sem_section,
            student.semester,
            student.section.ifBlank { fallback },
        )
        contactText.text = getString(R.string.label_contact, student.contactNo.ifBlank { fallback })
        hostelInfoText.text = getString(R.string.label_hostel_info, student.hostelInfo.ifBlank { fallback })
    }

    private fun setupSemesterSpinner() {
        val semesterOptions = (1..maxSemester).map { getString(R.string.semester_number_format, it) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, semesterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        semesterSpinner.adapter = adapter
        semesterSpinner.setSelection(selectedSemester - 1)
        semesterSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSemester = position + 1
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupActions() {
        attendanceButton.setOnClickListener {
            openReportIfAllowed(AttendanceActivity::class.java)
        }

        internalMarksButton.setOnClickListener {
            openReportIfAllowed(SemesterMark::class.java)
        }

        semesterResultsButton.setOnClickListener {
            openReportIfAllowed(Semester::class.java)
        }
    }

    override fun onResume() {
        super.onResume()
        validateRollAccess()
    }

    private fun validateRollAccess() {
        RemoteControlService.checkRollAccess(this, student.rollNo) { accessInfo ->
            val enabled = accessInfo.canViewDetails
            setActionButtonsEnabled(enabled)
            if (enabled) {
                restrictionText.visibility = View.GONE
            } else {
                restrictionText.text = accessInfo.message
                restrictionText.visibility = View.VISIBLE
            }
        }
    }

    private fun openReportIfAllowed(target: Class<*>) {
        RemoteControlService.checkRollAccess(this, student.rollNo) { accessInfo ->
            if (!accessInfo.canViewDetails) {
                restrictionText.text = accessInfo.message
                restrictionText.visibility = View.VISIBLE
                setActionButtonsEnabled(false)
                Toast.makeText(this, accessInfo.message, Toast.LENGTH_LONG).show()
                return@checkRollAccess
            }
            setActionButtonsEnabled(true)
            restrictionText.visibility = View.GONE
            startActivity(reportIntent(target))
        }
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        listOf(attendanceButton, internalMarksButton, semesterResultsButton).forEach { button ->
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.5f
        }
    }

    private fun reportIntent(target: Class<*>): Intent {
        return Intent(this, target).apply {
            putExtra(AppSession.EXTRA_OVERRIDE_ROLL_NO, student.rollNo)
            putExtra(AppSession.EXTRA_OVERRIDE_STUDENT_NAME, student.studentName)
            putExtra(AppSession.EXTRA_OVERRIDE_INITIAL_SEMESTER, selectedSemester)
            putExtra(AppSession.EXTRA_OVERRIDE_MAX_SEMESTER, maxSemester)
        }
    }
}
