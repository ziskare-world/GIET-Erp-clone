package com.example.gieterp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ScrollView
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

class SubjectwiseMarks : AppCompatActivity() {

    private val requestQueue by lazy { VolleyProvider.getRequestQueue(this) }

    private lateinit var tableLayout: TableLayout
    private lateinit var examTitleTextView: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var verticalScrollView: ScrollView
    private var activeExamId: String? = null
    private var activeStudentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_subjectwise_marks)
        SystemBarInsets.apply(findViewById(R.id.main))

        tableLayout = findViewById(R.id.tableLayout)
        examTitleTextView = findViewById(R.id.examIdTextView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshSubjectwise)
        verticalScrollView = findViewById(R.id.vScrollSubjectwise)

        val examId = intent.getStringExtra(AppSession.EXTRA_EXAM_ID)
        val studentId = intent.getStringExtra(AppSession.EXTRA_STUDENT_ID)

        if (!examId.isNullOrEmpty() && !studentId.isNullOrEmpty()) {
            activeExamId = examId
            activeStudentId = studentId
            setupSwipeToRefresh()
            getSubjectMarks(examId, studentId)
        } else {
            Toast.makeText(this, R.string.exam_or_student_id_missing, Toast.LENGTH_LONG).show()
            Log.e("SubjectwiseMarks", "Missing data -> examId: $examId, studentId: $studentId")
            finish()
        }
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
            val examId = activeExamId
            val studentId = activeStudentId
            if (examId.isNullOrBlank() || studentId.isNullOrBlank()) {
                swipeRefreshLayout.isRefreshing = false
                return@setOnRefreshListener
            }
            getSubjectMarks(examId, studentId, fromSwipeRefresh = true)
        }
    }

    private fun getSubjectMarks(examId: String, studentId: String, fromSwipeRefresh: Boolean = false) {
        val url = "https://gietuerp.in/ExamReport/GetAllSubjectMarksForStudents"
        if (fromSwipeRefresh) {
            swipeRefreshLayout.isRefreshing = true
        }

        val request = object : StringRequest(
            Request.Method.POST,
            url,
            { response ->
                Log.d("SubjectMarksResponse", response)
                try {
                    parseSubjectMarks(response)
                } catch (e: Exception) {
                    val parseMessage = e.message ?: getString(R.string.status_no_data)
                    Toast.makeText(this, getString(R.string.error_parse_format, parseMessage), Toast.LENGTH_LONG).show()
                    Log.e("SubjectMarksParse", "Failed to parse subject marks", e)
                }
                swipeRefreshLayout.isRefreshing = false
            },
            { error ->
                val errorMessage = error.message ?: getString(R.string.error_unknown_network)
                Toast.makeText(this, getString(R.string.error_network_format, errorMessage), Toast.LENGTH_LONG).show()
                Log.e("SubjectMarksError", errorMessage, error)
                swipeRefreshLayout.isRefreshing = false
            },
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "intExamScheduleMasterID" to examId,
                    "intStudentID" to studentId,
                )
            }

            @Throws(AuthFailureError::class)
            override fun getHeaders(): MutableMap<String, String> {
                return mutableMapOf("Content-Type" to "application/x-www-form-urlencoded")
            }
        }

        requestQueue.add(request)
    }

    private fun parseSubjectMarks(response: String) {
        tableLayout.removeAllViews()

        val json = JSONObject(response)
        val message = json.optString("message", getString(R.string.status_no_data))
        val dataArray = json.optJSONArray("data")

        if (dataArray == null || dataArray.length() == 0) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }

        examTitleTextView.text = dataArray.getJSONObject(0).optString("vchExamName", getString(R.string.exam_marks_fallback_title))

        val headerRow = TableRow(this)
        val headers = listOf(
            getString(R.string.header_subject_name),
            getString(R.string.header_total_marks),
            getString(R.string.header_marks_secured),
        )

        headers.forEach { header ->
            headerRow.addView(TableUiFactory.createHeaderCell(this, header))
        }
        tableLayout.addView(headerRow)

        for (index in 0 until dataArray.length()) {
            val subject = dataArray.getJSONObject(index)
            val row = TableRow(this)

            listOf(
                subject.optString("vchSubjectShortName", getString(R.string.common_not_available)),
                subject.optString("decTotalMark", getString(R.string.common_not_available)),
                subject.optString("decMarkSecured", getString(R.string.common_not_available)),
            ).forEach { value ->
                row.addView(TableUiFactory.createBodyCell(this, value))
            }

            tableLayout.addView(row)
        }
    }
}
