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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity2 : AppCompatActivity() {
    companion object {
        private const val ATTENDANCE_REQUEST_TAG = "dashboard_attendance_request"
    }

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
    private lateinit var fullAttendanceStatusText: TextView
    private lateinit var selectedAttendancePercentText: TextView
    private lateinit var selectedAttendanceStatusText: TextView
    private lateinit var semesterButton: ImageButton
    private lateinit var restrictionMessageText: TextView
    private lateinit var subjectSelectionSummaryText: TextView
    private lateinit var subjectSelectionContainer: ChipGroup
    private lateinit var dashboardAdContainer: FrameLayout
    private lateinit var privacyChoicesText: TextView
    private lateinit var bannerController: DashboardBannerAdController

    private var lastAttendanceResponse: String? = null
    private var attendanceRecords: List<AttendanceRecord> = emptyList()
    private var availableSubjects: List<String> = emptyList()
    private val selectedSubjects = linkedSetOf<String>()
    private var hasInitializedSubjectSelection = false
    private var activeRollNo: String? = null
    private var shouldOpenAttendanceAfterRefresh = false
    private var isRollRestricted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main2)
        AttendancePushTokenSync.requestNotificationPermissionIfNeeded(this)

        SystemBarInsets.apply(findViewById(R.id.main))

        loadingOverlay = findViewById(R.id.loadingOverlay)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDashboard)
        appTitleText = findViewById(R.id.appTitleText)
        rollnoView = findViewById(R.id.rollno)
        logoutButton = findViewById(R.id.buttonLogout)
        getAttendance = findViewById(R.id.getattendance)
        getSemesterMark = findViewById(R.id.getsemestermarks)
        attendancePercentText = findViewById(R.id.attendancePercentText)
        fullAttendanceStatusText = findViewById(R.id.bunkedClassText)
        selectedAttendancePercentText = findViewById(R.id.selectedAttendancePercentText)
        selectedAttendanceStatusText = findViewById(R.id.selectedAttendanceStatusText)
        semesterButton = findViewById(R.id.getSemester)
        restrictionMessageText = findViewById(R.id.restrictionMessageText)
        subjectSelectionSummaryText = findViewById(R.id.subjectSelectionSummaryText)
        subjectSelectionContainer = findViewById(R.id.subjectSelectionContainer)
        dashboardAdContainer = findViewById(R.id.dashboardAdContainer)
        privacyChoicesText = findViewById(R.id.privacyChoicesText)
        bannerController = DashboardBannerAdController(this, dashboardAdContainer)
        setSubjectSelectionEnabled(false)

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
            if (isRollRestricted) {
                Toast.makeText(this, R.string.roll_restricted_default_message, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val response = lastAttendanceResponse
            if (response.isNullOrEmpty()) {
                Toast.makeText(this, getString(R.string.attendance_loading_wait), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendResponse(response)
        }

        getSemesterMark.setOnClickListener {
            if (isRollRestricted) {
                Toast.makeText(this, R.string.roll_restricted_default_message, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, SemesterMark::class.java))
        }

        semesterButton.setOnClickListener {
            if (isRollRestricted) {
                Toast.makeText(this, R.string.roll_restricted_default_message, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, Semester::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val rollNo = activeRollNo
        if (!rollNo.isNullOrBlank()) {
            AttendancePushTokenSync.syncCurrentTokenIfPossible(applicationContext)
            RemoteControlService.heartbeat(applicationContext, rollNo)
            refreshDashboardAccessAndData()
        }
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

    override fun onStop() {
        requestQueue.cancelAll(ATTENDANCE_REQUEST_TAG)
        finishLoadingState()
        super.onStop()
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
            refreshDashboardAccessAndData()
        }
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
            R.color.accent_secondary,
            R.color.accent_primary,
        )
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.surface_card)
        swipeRefreshLayout.setOnRefreshListener {
            refreshDashboardAccessAndData(fromSwipeRefresh = true)
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

    private fun refreshDashboardAccessAndData(fromSwipeRefresh: Boolean = false) {
        val rollNo = activeRollNo
        if (rollNo.isNullOrBlank()) {
            finishLoadingState()
            return
        }

        if (fromSwipeRefresh) {
            swipeRefreshLayout.isRefreshing = true
        } else {
            loadingOverlay.visibility = View.VISIBLE
        }

        RemoteControlService.checkRollAccess(this, rollNo) { accessInfo ->
            if (!accessInfo.canViewDetails) {
                renderRestrictedState(accessInfo.message)
                finishLoadingState()
                shouldOpenAttendanceAfterRefresh = false
                return@checkRollAccess
            }

            renderAllowedState()
            fetchAttendance(
                rollNo = rollNo,
                semester = StudentAnalytics.calculateCurrentSemester(rollNo),
                fromSwipeRefresh = fromSwipeRefresh,
                manageLoadingState = false,
            )
        }
    }

    private fun fetchAttendance(
        rollNo: String,
        semester: Int,
        fromSwipeRefresh: Boolean = false,
        manageLoadingState: Boolean = true,
    ) {
        val url = "https://gietuerp.in/AttendanceReport/GetAttendanceByRollNo"
        if (manageLoadingState && fromSwipeRefresh) {
            swipeRefreshLayout.isRefreshing = true
        } else if (manageLoadingState) {
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

        request.tag = ATTENDANCE_REQUEST_TAG
        requestQueue.cancelAll(ATTENDANCE_REQUEST_TAG)
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

    private fun renderRestrictedState(message: String) {
        isRollRestricted = true
        lastAttendanceResponse = null
        attendanceRecords = emptyList()
        availableSubjects = emptyList()
        selectedSubjects.clear()
        hasInitializedSubjectSelection = false
        restrictionMessageText.text = message.ifBlank { getString(R.string.roll_restricted_default_message) }
        restrictionMessageText.visibility = View.VISIBLE
        renderAttendanceSummary(attendancePercentText, fullAttendanceStatusText, null, getString(R.string.roll_restricted_status))
        renderAttendanceSummary(selectedAttendancePercentText, selectedAttendanceStatusText, null, getString(R.string.roll_restricted_status))
        fullAttendanceStatusText.setTextColor(ContextCompat.getColor(this, R.color.danger))
        selectedAttendanceStatusText.setTextColor(ContextCompat.getColor(this, R.color.danger))
        subjectSelectionSummaryText.text = restrictionMessageText.text
        bindSubjectCheckboxes(emptyList())
        setSubjectSelectionEnabled(false)
        setDashboardActionsEnabled(false)
    }

    private fun renderAllowedState() {
        isRollRestricted = false
        restrictionMessageText.visibility = View.GONE
        if (attendanceRecords.isEmpty()) {
            subjectSelectionSummaryText.text = getString(R.string.subject_selection_loading)
        }
        setSubjectSelectionEnabled(true)
        setDashboardActionsEnabled(true)
    }

    private fun setDashboardActionsEnabled(enabled: Boolean) {
        listOf(getAttendance, getSemesterMark, semesterButton).forEach { button ->
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.45f
        }
    }

    private fun parseAttendance(response: String) {
        try {
            val json = JSONObject(response)
            val dataArray = json.optJSONArray("dataAttendance")

            if (dataArray == null || dataArray.length() == 0) {
                Toast.makeText(this, json.optString("message", getString(R.string.attendance_no_data)), Toast.LENGTH_LONG).show()
                attendanceRecords = emptyList()
                availableSubjects = emptyList()
                selectedSubjects.clear()
                hasInitializedSubjectSelection = false
                renderAttendanceSummary(attendancePercentText, fullAttendanceStatusText, null)
                renderAttendanceSummary(selectedAttendancePercentText, selectedAttendanceStatusText, null)
                bindSubjectCheckboxes(emptyList())
                updateSubjectSelectionSummary()
                setSubjectSelectionEnabled(true)
                return
            }

            val rowAttendanceRecords = buildList {
                for (index in 0 until dataArray.length()) {
                    val obj = dataArray.getJSONObject(index)
                    StudentAnalytics.extractAttendanceRecord(obj)?.let(::add)
                }
            }
            attendanceRecords = StudentAnalytics.extractAttendanceColumnRecords(dataArray)
                .map { record ->
                    record.copy(
                        subjectLabel = record.subjectLabel.ifBlank { getString(R.string.unknown_subject) },
                    )
                }

            val fullSummary = StudentAnalytics.summarizeAttendance(
                rowAttendanceRecords.map { it.attendedClasses to it.totalClasses },
            )
            val subjects = attendanceRecords
                .map { it.subjectLabel }
                .distinct()
                .sortedBy { it.uppercase(Locale.US) }

            syncSubjectSelection(subjects)

            renderAttendanceSummary(attendancePercentText, fullAttendanceStatusText, fullSummary)
            renderSelectedAttendanceSummary()
        } catch (e: Exception) {
            val parseMessage = e.message ?: getString(R.string.status_no_data)
            Toast.makeText(this, getString(R.string.error_parse_format, parseMessage), Toast.LENGTH_LONG).show()
            Log.e("AttendanceParse", "Failed to parse attendance", e)
        }
    }

    private fun syncSubjectSelection(subjects: List<String>) {
        val previousSubjects = availableSubjects.toSet()
        val wereAllSubjectsSelected = previousSubjects.isNotEmpty() && selectedSubjects.containsAll(previousSubjects)

        availableSubjects = subjects
        selectedSubjects.retainAll(subjects.toSet())

        if (!hasInitializedSubjectSelection || wereAllSubjectsSelected) {
            selectedSubjects.clear()
            val savedSubjects = loadSavedSubjectSelection()
            if (savedSubjects == null || wereAllSubjectsSelected) {
                selectedSubjects.addAll(subjects)
            } else {
                selectedSubjects.addAll(subjects.filter { savedSubjects.contains(it) })
            }
            hasInitializedSubjectSelection = subjects.isNotEmpty()
        }

        bindSubjectCheckboxes(subjects)
        persistSelectedSubjects()
        updateSubjectSelectionSummary()
        setSubjectSelectionEnabled(true)
    }

    private fun bindSubjectCheckboxes(subjects: List<String>) {
        subjectSelectionContainer.removeAllViews()

        if (subjects.isEmpty()) {
            return
        }

        subjects.forEach { subject ->
            val checkBox = Chip(this).apply {
                text = subject
                isCheckable = true
                isChecked = selectedSubjects.contains(subject)
                isClickable = true
                isFocusable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedSubjects.add(subject)
                    } else {
                        selectedSubjects.remove(subject)
                    }
                    persistSelectedSubjects()
                    renderSelectedAttendanceSummary()
                }
            }

            subjectSelectionContainer.addView(checkBox)
        }
    }

    private fun updateSubjectSelectionSummary() {
        subjectSelectionSummaryText.text = when {
            availableSubjects.isEmpty() -> getString(R.string.subject_selection_empty)
            selectedSubjects.isEmpty() -> getString(R.string.selected_attendance_none_selected)
            selectedSubjects.size == availableSubjects.size -> getString(
                R.string.subject_selection_all_selected,
                availableSubjects.size,
            )
            else -> getString(
                R.string.subject_selection_count,
                selectedSubjects.size,
                availableSubjects.size,
            )
        }
    }

    private fun renderSelectedAttendanceSummary() {
        updateSubjectSelectionSummary()
        val selectedSummary = StudentAnalytics.summarizeAttendance(
            attendanceRecords
                .filter { selectedSubjects.contains(it.subjectLabel) }
                .map { it.attendedClasses to it.totalClasses },
        )

        val emptyStatus = when {
            attendanceRecords.isEmpty() -> getString(R.string.selected_attendance_no_data)
            selectedSubjects.isEmpty() -> getString(R.string.selected_attendance_none_selected)
            else -> getString(R.string.selected_attendance_no_data)
        }

        renderAttendanceSummary(
            percentageView = selectedAttendancePercentText,
            statusView = selectedAttendanceStatusText,
            summary = selectedSummary,
            emptyStatus = emptyStatus,
        )
    }

    private fun persistSelectedSubjects() {
        val rollNo = activeRollNo?.trim()?.uppercase(Locale.US).orEmpty()
        if (rollNo.isBlank()) return

        val payload = JSONArray().apply {
            selectedSubjects.forEach { put(it) }
        }

        getSharedPreferences(AppSession.PREFERENCES_NAME, MODE_PRIVATE).edit {
            putString("${AppSession.KEY_SELECTED_ATTENDANCE_SUBJECTS_PREFIX}$rollNo", payload.toString())
        }
    }

    private fun loadSavedSubjectSelection(): Set<String>? {
        val rollNo = activeRollNo?.trim()?.uppercase(Locale.US).orEmpty()
        if (rollNo.isBlank()) return null

        val preferences = getSharedPreferences(AppSession.PREFERENCES_NAME, MODE_PRIVATE)
        val preferenceKey = "${AppSession.KEY_SELECTED_ATTENDANCE_SUBJECTS_PREFIX}$rollNo"
        if (!preferences.contains(preferenceKey)) {
            return null
        }

        val rawSelection = preferences.getString(preferenceKey, "[]").orEmpty()
        return try {
            val jsonArray = JSONArray(rawSelection)
            buildSet {
                for (index in 0 until jsonArray.length()) {
                    jsonArray.optString(index)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun setSubjectSelectionEnabled(enabled: Boolean) {
        val effectiveEnabled = enabled && !isRollRestricted
        subjectSelectionContainer.alpha = if (effectiveEnabled) 1f else 0.55f

        for (index in 0 until subjectSelectionContainer.childCount) {
            subjectSelectionContainer.getChildAt(index).isEnabled = effectiveEnabled
        }
    }

    private fun renderAttendanceSummary(
        percentageView: TextView,
        statusView: TextView,
        summary: AttendanceSummary?,
        emptyStatus: String = getString(R.string.status_no_data),
    ) {
        if (summary == null) {
            percentageView.text = getString(R.string.value_unknown)
            statusView.text = emptyStatus
            percentageView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            statusView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            return
        }

        percentageView.text = summary.percentageLabel()
        statusView.text = summary.statusLabel()
        val indicatorColor = if (summary.isAtOrAboveThreshold) {
            R.color.accent_positive
        } else {
            R.color.danger
        }
        percentageView.setTextColor(ContextCompat.getColor(this, indicatorColor))
        statusView.setTextColor(ContextCompat.getColor(this, indicatorColor))
    }
}
