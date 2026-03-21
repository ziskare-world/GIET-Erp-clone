package com.example.gieterp

import android.os.Bundle
import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.AuthFailureError
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import org.json.JSONObject

class AttendanceActivity : AppCompatActivity() {

    companion object {
        private const val ATTENDANCE_REQUEST_TAG = "attendance_report_request"
    }

    private val requestQueue by lazy { VolleyProvider.getRequestQueue(this) }

    private lateinit var tableLayout: TableLayout
    private lateinit var studentIdentityText: TextView
    private lateinit var loadingOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_attendance)

        findViewById<View>(R.id.main).applySystemBarsPadding()

        tableLayout = findViewById(R.id.tableLayout)
        studentIdentityText = findViewById(R.id.studentIdentityText)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        val overrideRollNo = intent.getStringExtra(AppSession.EXTRA_OVERRIDE_ROLL_NO)
        val overrideName = intent.getStringExtra(AppSession.EXTRA_OVERRIDE_STUDENT_NAME)
        val overrideSemester = intent.getIntExtra(AppSession.EXTRA_OVERRIDE_INITIAL_SEMESTER, -1)

        if (!overrideRollNo.isNullOrBlank()) {
            studentIdentityText.text = getString(
                R.string.special_student_identity_format,
                overrideName?.takeIf { it.isNotBlank() } ?: getString(R.string.common_not_available),
                overrideRollNo,
            )
            studentIdentityText.visibility = View.VISIBLE
        }

        val response = intent.getStringExtra(AppSession.EXTRA_ATTENDANCE_RESPONSE)
        when {
            !response.isNullOrEmpty() -> parseAttendance(response)
            !overrideRollNo.isNullOrBlank() && overrideSemester > 0 -> fetchAttendance(overrideRollNo, overrideSemester)
            else -> {
                Toast.makeText(this, R.string.attendance_data_missing, Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }
    }

    private fun parseAttendance(response: String) {
        try {
            tableLayout.removeAllViews()

            val json = JSONObject(response)
            val message = json.optString("message", getString(R.string.attendance_no_data))
            val dataArray = json.optJSONArray("dataAttendance")

            if (dataArray == null || dataArray.length() == 0) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return
            }

            val firstObject = dataArray.getJSONObject(0)
            val columnKeys = mutableListOf("AttendanceDate")
            val iterator = firstObject.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (key != "AttendanceDate") {
                    columnKeys.add(key)
                }
            }

            val headerRow = TableRow(this)
            columnKeys.forEach { key ->
                val label = if (key == "AttendanceDate") getString(R.string.header_attendance_date) else key
                headerRow.addView(TableUiFactory.createHeaderCell(this, label))
            }
            tableLayout.addView(headerRow)

            for (index in 0 until dataArray.length()) {
                val rowData = dataArray.getJSONObject(index)
                val row = TableRow(this)

                columnKeys.forEach { key ->
                    val value = rowData.optString(key, getString(R.string.common_not_available))
                    val isAlertCell = if (value.contains('/')) {
                        val parts = value.split('/', limit = 2)
                        val attended = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
                        val total = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
                        attended != null && total != null && attended < total
                    } else {
                        false
                    }
                    row.addView(TableUiFactory.createBodyCell(this, value, isAlertCell))
                }

                tableLayout.addView(row)
            }
        } catch (e: Exception) {
            val parseMessage = e.message ?: getString(R.string.status_no_data)
            Toast.makeText(this, getString(R.string.error_parse_format, parseMessage), Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchAttendance(rollNo: String, semester: Int) {
        loadingOverlay.visibility = View.VISIBLE
        val request = object : StringRequest(
            Request.Method.POST,
            "https://gietuerp.in/AttendanceReport/GetAttendanceByRollNo",
            { response ->
                loadingOverlay.visibility = View.GONE
                parseAttendance(response)
            },
            { error ->
                loadingOverlay.visibility = View.GONE
                val errorMessage = error.message ?: getString(R.string.error_unknown_network)
                Toast.makeText(this, getString(R.string.error_network_format, errorMessage), Toast.LENGTH_LONG).show()
            },
        ) {
            override fun getParams() = mutableMapOf(
                "vvchRollNo" to rollNo,
                "vintSemester" to semester.toString(),
                "vdtmStartDate" to "",
                "vdtmEndDate" to "",
            )

            @Throws(AuthFailureError::class)
            override fun getHeaders() = mutableMapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
            )
        }

        request.tag = ATTENDANCE_REQUEST_TAG
        requestQueue.add(request)
    }

    override fun onStop() {
        super.onStop()
        requestQueue.cancelAll(ATTENDANCE_REQUEST_TAG)
    }
}
