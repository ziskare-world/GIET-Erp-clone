package com.example.gieterp

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import org.json.JSONArray
import org.json.JSONObject

class Semester : AppCompatActivity() {

    private val requestQueue by lazy { VolleyProvider.getRequestQueue(this) }

    private lateinit var tableLayout: TableLayout
    private lateinit var semesterSpinner: Spinner
    private lateinit var studentIdentityText: TextView
    private lateinit var loadingOverlay: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var scrollView: ScrollView

    private var selectedSemester = 1
    private var maxSemester = 1
    private var rollNo: String? = null
    private var isOverrideStudent = false
    private var ignoreInitialSelection = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_semester)

        findViewById<View>(R.id.main).applySystemBarsPadding()

        tableLayout = findViewById(R.id.tableLayout)
        semesterSpinner = findViewById(R.id.semesterSpinner)
        studentIdentityText = findViewById(R.id.studentIdentityText)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshSemester)
        scrollView = findViewById(R.id.scrollView)

        val overrideRollNo = intent.getStringExtra(AppSession.EXTRA_OVERRIDE_ROLL_NO)
        val overrideName = intent.getStringExtra(AppSession.EXTRA_OVERRIDE_STUDENT_NAME)

        if (!overrideRollNo.isNullOrBlank()) {
            isOverrideStudent = true
            rollNo = overrideRollNo
            maxSemester = intent.getIntExtra(
                AppSession.EXTRA_OVERRIDE_MAX_SEMESTER,
                StudentAnalytics.calculateCurrentSemester(overrideRollNo),
            ).coerceIn(1, 8)
            selectedSemester = intent.getIntExtra(
                AppSession.EXTRA_OVERRIDE_INITIAL_SEMESTER,
                maxSemester,
            ).coerceIn(1, maxSemester)
            studentIdentityText.text = getString(
                R.string.special_student_identity_format,
                overrideName?.takeIf { it.isNotBlank() } ?: getString(R.string.common_not_available),
                overrideRollNo,
            )
            studentIdentityText.visibility = View.VISIBLE
        } else {
            val sharedPreferences = getSharedPreferences(AppSession.PREFERENCES_NAME, MODE_PRIVATE)
            rollNo = sharedPreferences.getString(AppSession.KEY_ROLL_NO, null)

            if (rollNo.isNullOrBlank()) {
                Toast.makeText(this, R.string.roll_number_missing, Toast.LENGTH_LONG).show()
                finish()
                return
            }

            selectedSemester = StudentAnalytics.calculateCurrentSemester(rollNo!!)
            maxSemester = selectedSemester
        }

        setupSpinner(maxSemester, selectedSemester)
        setupSwipeToRefresh()
        loadSemesterData(selectedSemester)
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
            R.color.accent_secondary,
            R.color.accent_primary,
        )
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.surface_card)
        swipeRefreshLayout.setOnRefreshListener {
            loadSemesterData(selectedSemester, fromSwipeRefresh = true)
        }
    }

    private fun showLoader(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        scrollView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun loadSemesterData(semester: Int, fromSwipeRefresh: Boolean = false) {
        val currentRollNo = rollNo ?: return
        fetchStudentId(semester, currentRollNo, fromSwipeRefresh)
    }

    private fun fetchStudentId(semester: Int, roll: String, fromSwipeRefresh: Boolean) {
        if (fromSwipeRefresh) {
            swipeRefreshLayout.isRefreshing = true
        } else {
            showLoader(true)
        }

        val url = "https://gietuerp.in/ExamReport/GetAllScheduledExamForStudents"
        val request = object : StringRequest(
            Request.Method.POST,
            url,
            { response ->
                try {
                    val json = JSONObject(response)
                    val data = json.optJSONArray("data")
                    val studentId = if (data != null && data.length() > 0) {
                        data.getJSONObject(0).optInt("intStudentID", -1)
                    } else {
                        -1
                    }

                    if (studentId > 0) {
                        if (!isOverrideStudent) {
                            getSharedPreferences(AppSession.PREFERENCES_NAME, MODE_PRIVATE).edit {
                                putInt(AppSession.KEY_STUDENT_ID, studentId)
                            }
                        }
                        getSemesterGrades(semester, studentId, fromSwipeRefresh)
                    } else {
                        finishLoading(fromSwipeRefresh)
                        showError(getString(R.string.failed_load_student_id))
                    }
                } catch (e: Exception) {
                    finishLoading(fromSwipeRefresh)
                    showError(getString(R.string.failed_parse_student_details))
                }
            },
            { error ->
                finishLoading(fromSwipeRefresh)
                showError(error.message ?: getString(R.string.failed_load_student_id))
            },
        ) {
            override fun getParams() = mutableMapOf(
                "intSemester" to semester.toString(),
                "vchRollNo" to roll,
            )

            override fun getHeaders() = mutableMapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
            )
        }

        requestQueue.add(request)
    }

    private fun getSemesterGrades(semester: Int, studentId: Int, fromSwipeRefresh: Boolean) {
        val url = "https://gietuerp.in/Student/GetSubjectWiseGrades?semester=$semester&studentId=$studentId"
        val request = StringRequest(
            Request.Method.GET,
            url,
            { response ->
                runCatching {
                    parseGrades(response)
                }.onFailure {
                    showError(getString(R.string.failed_parse_grades))
                }
                finishLoading(fromSwipeRefresh)
            },
            { error ->
                finishLoading(fromSwipeRefresh)
                showError(error.message ?: getString(R.string.failed_load_grades))
            },
        )

        requestQueue.add(request)
    }

    private fun finishLoading(fromSwipeRefresh: Boolean) {
        if (fromSwipeRefresh) {
            swipeRefreshLayout.isRefreshing = false
        } else {
            showLoader(false)
        }
    }

    private fun parseGrades(response: String) {
        tableLayout.removeAllViews()

        val grades = JSONArray(response)
        if (grades.length() == 0) {
            showError(getString(R.string.no_grades_for_semester))
            return
        }

        val header = TableRow(this)
        listOf(
            getString(R.string.header_subject),
            getString(R.string.header_grade),
            getString(R.string.header_sgpa),
        ).forEach { title ->
            header.addView(TableUiFactory.createHeaderCell(this, title))
        }
        tableLayout.addView(header)

        for (index in 0 until grades.length()) {
            val grade = grades.getJSONObject(index)
            val row = TableRow(this)

            listOf(
                grade.optString("subjectCode", getString(R.string.common_not_available)),
                grade.optString("grade", getString(R.string.common_not_available)),
                grade.optString("vchSGPA", getString(R.string.common_not_available)),
            ).forEach { value ->
                row.addView(TableUiFactory.createBodyCell(this, value))
            }

            tableLayout.addView(row)
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupSpinner(maxAvailableSemester: Int, initialSemester: Int) {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            (1..maxAvailableSemester).map { getString(R.string.semester_number_format, it) },
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        semesterSpinner.adapter = adapter
        semesterSpinner.setSelection(initialSemester - 1)

        semesterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedSemester = position + 1
                if (ignoreInitialSelection) {
                    ignoreInitialSelection = false
                    return
                }
                loadSemesterData(selectedSemester)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }
}
