package com.example.gieterp

import android.os.Bundle
import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class AttendanceActivity : AppCompatActivity() {

    private lateinit var tableLayout: TableLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_attendance)

        findViewById<View>(R.id.main).applySystemBarsPadding()

        tableLayout = findViewById(R.id.tableLayout)

        val response = intent.getStringExtra(AppSession.EXTRA_ATTENDANCE_RESPONSE)
        if (response.isNullOrEmpty()) {
            Toast.makeText(this, R.string.attendance_data_missing, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        parseAttendance(response)
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
}
