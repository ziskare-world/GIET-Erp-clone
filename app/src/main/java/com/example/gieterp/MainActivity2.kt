package com.example.gieterp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.AuthFailureError
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import org.json.JSONObject

class MainActivity2 : AppCompatActivity() {

    private val requestQueue by lazy { VolleyProvider.getRequestQueue(this) }
    private val adsConsentManager by lazy { AdsConsentManager.getInstance(applicationContext) }

    private lateinit var loadingOverlay: View
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var appTitleText: TextView
    private lateinit var rollnoView: TextView
    private lateinit var logoutButton: Button
    private lateinit var getAttendance: ImageButton
    private lateinit var getSemesterMark: ImageButton
    private lateinit var attendancePercentText: TextView
    private lateinit var bunkedClassText: TextView
    private lateinit var semesterButton: ImageButton
    private lateinit var dashboardAdContainer: FrameLayout
    private lateinit var privacyChoicesText: TextView
    private lateinit var bannerController: DashboardBannerAdController

    private var lastAttendanceResponse: String? = null
    private var activeRollNo: String? = null
    private var shouldOpenAttendanceAfterRefresh = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main2)
        AttendancePushTokenSync.requestNotificationPermissionIfNeeded(this)

        findViewById<View>(R.id.main).applySystemBarsPadding()

        loadingOverlay = findViewById(R.id.loadingOverlay)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)
        appTitleText = findViewById(R.id.appTitleText)
        rollnoView = findViewById(R.id.rollno)
        logoutButton = findViewById(R.id.buttonLogout)
        getAttendance = findViewById(R.id.getattendance)
        getSemesterMark = findViewById(R.id.getsemestermarks)
        attendancePercentText = findViewById(R.id.attendancePercentText)
        bunkedClassText = findViewById(R.id.bunkedClassText)
        semesterButton = findViewById(R.id.getSemester)
        dashboardAdContainer = findViewById(R.id.dashboardAdContainer)
        privacyChoicesText = findViewById(R.id.privacyChoicesText)
        bannerController = DashboardBannerAdController(this, dashboardAdContainer)

        val sharedPref = getSharedPreferences(AppSession.PREFERENCES_NAME, MODE_PRIVATE)
        val rollNo = sharedPref.getString(AppSession.KEY_ROLL_NO, null)

        if (rollNo.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.roll_number_not_found_sign_in), Toast.LENGTH_LONG).show()
            Log.e("MainActivity2", "Roll number not found in SharedPreferences")
            val loginIntent = Intent(this, MainActivity::class.java)
            AttendanceNotificationIntents.copyExtras(intent, loginIntent)
            startActivity(loginIntent)
            finish()
            return
        }

        appTitleText.text = getString(R.string.app_name_with_version, getString(R.string.giet_erp), AppVersion.name(this))
        rollnoView.text = getString(R.string.roll_no_display_format, rollNo)
        activeRollNo = rollNo
        shouldOpenAttendanceAfterRefresh = AttendanceNotificationIntents.shouldOpenAttendance(intent)
        setupSwipeToRefresh()
        setupAdsFooter()
        AttendancePushTokenSync.syncCurrentTokenIfPossible(applicationContext)
        fetchAttendance(rollNo, StudentAnalytics.calculateCurrentSemester(rollNo))

        logoutButton.setOnClickListener {
            AttendancePushTokenSync.unregisterCurrentToken(applicationContext, rollNo)
            sharedPref.edit {
                remove(AppSession.KEY_ROLL_NO)
                remove(AppSession.KEY_STUDENT_ID)
            }
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        getAttendance.setOnClickListener {
            val response = lastAttendanceResponse
            if (response.isNullOrEmpty()) {
                Toast.makeText(this, getString(R.string.attendance_loading_wait), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendResponse(response)
        }

        getSemesterMark.setOnClickListener {
            startActivity(Intent(this, SemesterMark::class.java))
        }

        semesterButton.setOnClickListener {
            startActivity(Intent(this, Semester::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        AppUpdateChecker.checkForUpdates(this)
        if (adsConsentManager.canRequestAds) {
            bannerController.loadBannerIfNeeded()
        }
        bannerController.onResume()
    }

    override fun onPause() {
        bannerController.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        bannerController.onDestroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (AttendanceNotificationIntents.shouldOpenAttendance(intent)) {
            shouldOpenAttendanceAfterRefresh = true
            val rollNo = activeRollNo
            if (!rollNo.isNullOrBlank()) {
                fetchAttendance(rollNo, StudentAnalytics.calculateCurrentSemester(rollNo))
            }
        }
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
            R.color.accent_secondary,
            R.color.accent_primary,
        )
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.surface_card)
        swipeRefreshLayout.setOnRefreshListener {
            val rollNo = activeRollNo
            if (rollNo.isNullOrBlank()) {
                swipeRefreshLayout.isRefreshing = false
                return@setOnRefreshListener
            }
            fetchAttendance(
                rollNo = rollNo,
                semester = StudentAnalytics.calculateCurrentSemester(rollNo),
                fromSwipeRefresh = true,
            )
        }
    }

    private fun setupAdsFooter() {
        bannerController.showContainer(false)
        updatePrivacyChoicesVisibility()
        privacyChoicesText.setOnClickListener {
            adsConsentManager.showPrivacyOptionsForm(this) {
                updatePrivacyChoicesVisibility()
                if (adsConsentManager.canRequestAds) {
                    (application as GietErpApp).initializeAdsIfNeeded()
                    bannerController.loadBannerIfNeeded()
                } else {
                    bannerController.showContainer(false)
                }
            }
        }

        adsConsentManager.gatherConsent(this) {
            updatePrivacyChoicesVisibility()
            if (adsConsentManager.canRequestAds) {
                (application as GietErpApp).initializeAdsIfNeeded()
                bannerController.loadBannerIfNeeded()
            } else {
                bannerController.showContainer(false)
            }
        }
    }

    private fun updatePrivacyChoicesVisibility() {
        privacyChoicesText.visibility = if (adsConsentManager.isPrivacyOptionsRequired) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun fetchAttendance(rollNo: String, semester: Int, fromSwipeRefresh: Boolean = false) {
        val url = "https://gietuerp.in/AttendanceReport/GetAttendanceByRollNo"
        if (fromSwipeRefresh) {
            swipeRefreshLayout.isRefreshing = true
        } else {
            loadingOverlay.visibility = View.VISIBLE
        }

        val request = object : StringRequest(
            Request.Method.POST,
            url,
            { response ->
                finishLoadingState()
                lastAttendanceResponse = response
                parseAttendance(response)
                if (shouldOpenAttendanceAfterRefresh) {
                    shouldOpenAttendanceAfterRefresh = false
                    sendResponse(response)
                }
            },
            { error ->
                finishLoadingState()
                shouldOpenAttendanceAfterRefresh = false
                val errorMessage = error.message ?: getString(R.string.error_unknown_network)
                Toast.makeText(this, getString(R.string.error_network_format, errorMessage), Toast.LENGTH_LONG).show()
                Log.e("AttendanceError", errorMessage, error)
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

        requestQueue.add(request)
    }

    private fun finishLoadingState() {
        loadingOverlay.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
    }

    private fun sendResponse(response: String) {
        val intent = Intent(this, AttendanceActivity::class.java)
        intent.putExtra(AppSession.EXTRA_ATTENDANCE_RESPONSE, response)
        startActivity(intent)
    }

    private fun parseAttendance(response: String) {
        try {
            val json = JSONObject(response)
            val dataArray = json.optJSONArray("dataAttendance")

            if (dataArray == null || dataArray.length() == 0) {
                Toast.makeText(this, json.optString("message", getString(R.string.attendance_no_data)), Toast.LENGTH_LONG).show()
                attendancePercentText.text = getString(R.string.value_unknown)
                bunkedClassText.text = getString(R.string.value_unknown)
                attendancePercentText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                bunkedClassText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                return
            }

            val attendanceRecords = buildList<Pair<Double, Double>> {
                for (index in 0 until dataArray.length()) {
                    val obj = dataArray.getJSONObject(index)
                    StudentAnalytics.extractAttendanceCount(
                        totalValue = obj.optString("Total", ""),
                        attendedValue = obj.optString("Attended", ""),
                    )?.let(::add)
                }
            }

            val summary = StudentAnalytics.summarizeAttendance(attendanceRecords)
            if (summary == null) {
                attendancePercentText.text = getString(R.string.value_unknown)
                bunkedClassText.text = getString(R.string.status_no_data)
                attendancePercentText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                bunkedClassText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                return
            }

            attendancePercentText.text = summary.percentageLabel()
            bunkedClassText.text = summary.statusLabel()
            val indicatorColor = if (summary.isAtOrAboveThreshold) {
                R.color.accent_positive
            } else {
                R.color.danger
            }
            attendancePercentText.setTextColor(ContextCompat.getColor(this, indicatorColor))
            bunkedClassText.setTextColor(ContextCompat.getColor(this, indicatorColor))
        } catch (e: Exception) {
            val parseMessage = e.message ?: getString(R.string.status_no_data)
            Toast.makeText(this, getString(R.string.error_parse_format, parseMessage), Toast.LENGTH_LONG).show()
            Log.e("AttendanceParse", "Failed to parse attendance", e)
        }
    }
}
