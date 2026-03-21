package com.example.gieterp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.AuthFailureError
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import org.json.JSONArray
import org.json.JSONObject

class SpecialSearchResultsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BRANCH_ID = "extra_branch_id"
        const val EXTRA_BRANCH_LABEL = "extra_branch_label"
        const val EXTRA_SEMESTER = "extra_semester"

        private const val SEARCH_REQUEST_TAG = "special_student_results_search"
    }

    private val requestQueue by lazy { VolleyProvider.getRequestQueue(this) }

    private lateinit var backButton: Button
    private lateinit var branchSummaryText: TextView
    private lateinit var semesterSummaryText: TextView
    private lateinit var rollNoInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var resultsCountText: TextView
    private lateinit var studentListView: ListView
    private lateinit var emptyStateText: TextView
    private lateinit var loadingOverlay: View
    private lateinit var studentAdapter: SpecialStudentAdapter

    private var branchId: Int = 0
    private var branchLabel: String = ""
    private var semester: Int = 0
    private var allStudents: List<SpecialStudent> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_special_search_results)

        SystemBarInsets.apply(findViewById(R.id.main))

        branchId = intent.getIntExtra(EXTRA_BRANCH_ID, 0)
        semester = intent.getIntExtra(EXTRA_SEMESTER, 0)
        branchLabel = intent.getStringExtra(EXTRA_BRANCH_LABEL)
            ?: SpecialDirectoryConfig.branchShortLabelById[branchId]
            ?: SpecialDirectoryConfig.branchOptions.first().label

        if (semester <= 0) {
            Toast.makeText(this, R.string.special_select_semester_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        bindSummary()
        setupSearch()
        fetchStudents()
    }

    private fun bindViews() {
        backButton = findViewById(R.id.buttonBack)
        branchSummaryText = findViewById(R.id.branchSummaryText)
        semesterSummaryText = findViewById(R.id.semesterSummaryText)
        rollNoInput = findViewById(R.id.rollNoInput)
        nameInput = findViewById(R.id.nameInput)
        resultsCountText = findViewById(R.id.resultsCountText)
        studentListView = findViewById(R.id.studentListView)
        emptyStateText = findViewById(R.id.emptyStateText)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        studentAdapter = SpecialStudentAdapter(this, emptyList())
        studentListView.adapter = studentAdapter
        studentListView.emptyView = emptyStateText
        studentListView.setOnItemClickListener { _, _, position, _ ->
            openStudentDetails(studentAdapter.getItem(position))
        }

        backButton.setOnClickListener { finish() }
    }

    private fun bindSummary() {
        branchSummaryText.text = getString(R.string.special_results_branch, branchLabel)
        semesterSummaryText.text = getString(R.string.special_results_semester, semester)
    }

    private fun setupSearch() {
        val searchWatcher = object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(text: Editable?) {
                applyFilter()
            }
        }

        rollNoInput.addTextChangedListener(searchWatcher)
        nameInput.addTextChangedListener(searchWatcher)
    }

    private fun fetchStudents() {
        loadingOverlay.visibility = View.VISIBLE
        requestQueue.cancelAll(SEARCH_REQUEST_TAG)

        val request = object : StringRequest(
            Request.Method.POST,
            "https://gietuerp.in/Student/GetAll",
            { response ->
                loadingOverlay.visibility = View.GONE
                allStudents = parseStudents(response)
                applyFilter()
            },
            { error ->
                loadingOverlay.visibility = View.GONE
                val message = error.message ?: getString(R.string.special_data_load_failed)
                Toast.makeText(this, getString(R.string.error_network_format, message), Toast.LENGTH_LONG).show()
            },
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "intProgramID" to SpecialDirectoryConfig.PROGRAM_ID.toString(),
                    "intBranchID" to branchId.toString(),
                    "intSemester" to semester.toString(),
                    "vchRollNo" to "",
                    "vchName" to "",
                )
            }

            @Throws(AuthFailureError::class)
            override fun getHeaders(): MutableMap<String, String> {
                return mutableMapOf("Content-Type" to "application/x-www-form-urlencoded")
            }
        }

        request.tag = SEARCH_REQUEST_TAG
        requestQueue.add(request)
    }

    private fun parseStudents(response: String): List<SpecialStudent> {
        return runCatching {
            val json = JSONObject(response)
            val dataArray = json.optJSONArray("data") ?: JSONArray()
            buildList {
                for (index in 0 until dataArray.length()) {
                    val jsonObject = dataArray.getJSONObject(index)
                    add(
                        SpecialStudent(
                            rollNo = jsonObject.optString("vchRollNo", getString(R.string.common_not_available)),
                            registrationNo = jsonObject.optString("vchRegistrationNo", getString(R.string.common_not_available)),
                            studentName = jsonObject.optString("vchName", getString(R.string.common_not_available)),
                            program = jsonObject.optString("vchProgramShortName", "BTech"),
                            branch = resolveBranchName(jsonObject),
                            semester = jsonObject.optString("intSemester", semester.toString()).toIntOrNull() ?: semester,
                            section = jsonObject.optString("vchSection", getString(R.string.common_not_available)),
                            contactNo = jsonObject.optString("vchContactNo", getString(R.string.common_not_available)),
                            hostelInfo = jsonObject.optString("vchHostelShortName", getString(R.string.common_not_available)),
                        ),
                    )
                }
            }
        }.getOrElse {
            Toast.makeText(this, R.string.special_data_load_failed, Toast.LENGTH_LONG).show()
            emptyList()
        }
    }

    private fun resolveBranchName(jsonObject: JSONObject): String {
        val branchName = jsonObject.optString("vchBranchName")
        if (branchName.isNotBlank() && branchName != "null") {
            return branchName
        }

        val branchValue = jsonObject.optInt("intBranchID", -1)
        return jsonObject.optString("vchBranchShortName").takeIf { it.isNotBlank() && it != "null" }
            ?: SpecialDirectoryConfig.branchShortLabelById[branchValue]
            ?: jsonObject.optString("vchBranchShortName", getString(R.string.common_not_available))
    }

    private fun applyFilter() {
        val rollQuery = rollNoInput.text?.toString()?.trim()?.lowercase().orEmpty()
        val nameQuery = nameInput.text?.toString()?.trim()?.lowercase().orEmpty()

        val filteredList = allStudents.filter { student ->
            val matchesRoll = rollQuery.isEmpty() || student.rollNo.lowercase().contains(rollQuery)
            val matchesName = nameQuery.isEmpty() || student.studentName.lowercase().contains(nameQuery)
            matchesRoll && matchesName
        }

        studentAdapter.submitList(filteredList)
        resultsCountText.text = getString(R.string.special_results_count, filteredList.size)
    }

    private fun openStudentDetails(student: SpecialStudent) {
        loadingOverlay.visibility = View.VISIBLE
        RemoteControlService.checkRollAccess(this, student.rollNo) { accessInfo ->
            loadingOverlay.visibility = View.GONE
            if (!accessInfo.canViewDetails) {
                Toast.makeText(this, accessInfo.message, Toast.LENGTH_LONG).show()
                return@checkRollAccess
            }

            val detailIntent = Intent(this, SpecialStudentDetailActivity::class.java).apply {
                SpecialStudentContract.putStudentExtras(this, student)
                putExtra(AppSession.EXTRA_OVERRIDE_INITIAL_SEMESTER, semester)
                putExtra(AppSession.EXTRA_OVERRIDE_MAX_SEMESTER, student.semester.coerceIn(1, 8))
            }
            startActivity(detailIntent)
        }
    }

    override fun onStop() {
        super.onStop()
        requestQueue.cancelAll(SEARCH_REQUEST_TAG)
    }
}
