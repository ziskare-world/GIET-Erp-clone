package com.example.gieterp

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.AuthFailureError
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import org.json.JSONObject

class SemesterMark : AppCompatActivity() {

    private val requestQueue by lazy { VolleyProvider.getRequestQueue(this) }

    private lateinit var tableLayout: TableLayout
    private lateinit var semesterSpinner: Spinner
    private lateinit var studentIdentityText: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var verticalScrollView: ScrollView
    private var currentSemester = 1
    private var maxSemester = 1
    private var rollNo: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_semester_mark)

        findViewById<View>(R.id.main).applySystemBarsPadding()

        tableLayout = findViewById(R.id.tableLayout)
        semesterSpinner = findViewById(R.id.semesterSpinner)
        studentIdentityText = findViewById(R.id.studentIdentityText)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshSemesterMark)
        verticalScrollView = findViewById(R.id.vScrollSemesterMark)

        val overrideRollNo = intent.getStringExtra(AppSession.EXTRA_OVERRIDE_ROLL_NO)
        val overrideName = intent.getStringExtra(AppSession.EXTRA_OVERRIDE_STUDENT_NAME)

        if (!overrideRollNo.isNullOrBlank()) {
            rollNo = overrideRollNo
            maxSemester = intent.getIntExtra(
                AppSession.EXTRA_OVERRIDE_MAX_SEMESTER,
                StudentAnalytics.calculateCurrentSemester(overrideRollNo),
            ).coerceIn(1, 8)
            currentSemester = intent.getIntExtra(
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
            val sharedPref = getSharedPreferences(AppSession.PREFERENCES_NAME, MODE_PRIVATE)
            rollNo = sharedPref.getString(AppSession.KEY_ROLL_NO, null)

            if (rollNo.isNullOrBlank()) {
                Toast.makeText(this, R.string.roll_number_not_found_local, Toast.LENGTH_LONG).show()
                finish()
                return
            }

            currentSemester = StudentAnalytics.calculateCurrentSemester(rollNo!!)
            maxSemester = currentSemester
        }

        setupSemesterSpinner(maxSemester, currentSemester)
        setupSwipeToRefresh()
        getInternalMarks(rollNo!!, currentSemester)
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
            R.color.accent_secondary,
            R.color.accent_primary,
        )
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.surface_card)
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            verticalScrollView.canScrollVertically(-1)
        }
        swipeRefreshLayout.setOnRefreshListener {
            val activeRollNo = rollNo
            if (activeRollNo.isNullOrBlank()) {
                swipeRefreshLayout.isRefreshing = false
                return@setOnRefreshListener
            }
            getInternalMarks(activeRollNo, currentSemester, fromSwipeRefresh = true)
        }
    }

    private fun setupSemesterSpinner(maxAvailableSemester: Int, initialSemester: Int) {
        val semesterList = (1..maxAvailableSemester).map { getString(R.string.semester_number_format, it) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, semesterList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        semesterSpinner.adapter = adapter
        semesterSpinner.setSelection(initialSemester - 1)

        semesterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedSemester = position + 1
                if (selectedSemester == currentSemester) {
                    return
                }

                currentSemester = selectedSemester
                Toast.makeText(
                    this@SemesterMark,
                    getString(R.string.loading_semester_marks, selectedSemester),
                    Toast.LENGTH_SHORT,
                ).show()
                getInternalMarks(rollNo!!, currentSemester)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun getInternalMarks(rollNo: String, semester: Int, fromSwipeRefresh: Boolean = false) {
        val url = "https://gietuerp.in/ExamReport/GetAllScheduledExamForStudents"
        if (fromSwipeRefresh) {
            swipeRefreshLayout.isRefreshing = true
        }

        val request = object : StringRequest(
            Request.Method.POST,
            url,
            { response ->
                Log.d("ExamResponse", response)
                try {
                    parseInternalMarks(response)
                } catch (e: Exception) {
                    val parseMessage = e.message ?: getString(R.string.status_no_data)
                    Toast.makeText(this, getString(R.string.error_parse_format, parseMessage), Toast.LENGTH_LONG).show()
                    Log.e("ExamParse", "Failed to parse internal marks", e)
                }
                swipeRefreshLayout.isRefreshing = false
            },
            { error ->
                val errorMessage = error.message ?: getString(R.string.error_unknown_network)
                Toast.makeText(this, getString(R.string.error_network_format, errorMessage), Toast.LENGTH_LONG).show()
                Log.e("ExamError", errorMessage, error)
                swipeRefreshLayout.isRefreshing = false
            },
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "intSemester" to semester.toString(),
                    "vchRollNo" to rollNo,
                )
            }

            @Throws(AuthFailureError::class)
            override fun getHeaders(): MutableMap<String, String> {
                return mutableMapOf("Content-Type" to "application/x-www-form-urlencoded")
            }
        }

        requestQueue.add(request)
    }

    private fun parseInternalMarks(response: String) {
        tableLayout.removeAllViews()

        val json = JSONObject(response)
        val message = json.optString("message", getString(R.string.status_no_data))
        val dataArray = json.optJSONArray("data")

        if (dataArray == null || dataArray.length() == 0) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }

        val headerRow = TableRow(this)
        val headers = listOf(
            getString(R.string.header_exam_name),
            getString(R.string.header_total_marks),
            getString(R.string.header_marks_secured),
            getString(R.string.header_subject_marks),
        )

        headers.forEach { header ->
            headerRow.addView(TableUiFactory.createHeaderCell(this, header))
        }
        tableLayout.addView(headerRow)

        for (index in 0 until dataArray.length()) {
            val exam = dataArray.getJSONObject(index)
            val row = TableRow(this)

            val examName = exam.optString("vchExamName", getString(R.string.common_not_available))
            val totalMarks = exam.optString("decTotalMark", getString(R.string.common_not_available))
            val securedMarks = exam.optString("decMarkSecured", getString(R.string.common_not_available))
            val examId = exam.optString("intExamScheduleMasterID", getString(R.string.common_not_available))
            val studentId = exam.optString("intStudentID", getString(R.string.common_not_available))

            listOf(examName, totalMarks, securedMarks).forEach { value ->
                row.addView(TableUiFactory.createBodyCell(this, value))
            }

            val button = TableUiFactory.createActionButton(this, getString(R.string.view_marks)) {
                val intent = Intent(this@SemesterMark, SubjectwiseMarks::class.java)
                intent.putExtra(AppSession.EXTRA_EXAM_ID, examId)
                intent.putExtra(AppSession.EXTRA_STUDENT_ID, studentId)
                startActivity(intent)
            }
            val isMarksLinkAvailable = examId != getString(R.string.common_not_available) &&
                studentId != getString(R.string.common_not_available)
            button.isEnabled = isMarksLinkAvailable
            button.alpha = if (isMarksLinkAvailable) 1f else 0.55f

            row.addView(button)
            tableLayout.addView(row)
        }
    }
}
