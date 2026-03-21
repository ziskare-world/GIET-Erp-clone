package com.example.gieterp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.AdapterView

class SpecialAccessActivity : AppCompatActivity() {

    private lateinit var closeButton: Button
    private lateinit var branchSpinner: Spinner
    private lateinit var semesterSpinner: Spinner
    private lateinit var openResultsButton: Button

    private var selectedBranchId = 0
    private var selectedBranchLabel = ""
    private var selectedSemester: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_special_access)

        SystemBarInsets.apply(findViewById(R.id.main))

        closeButton = findViewById(R.id.buttonClose)
        branchSpinner = findViewById(R.id.branchSpinner)
        semesterSpinner = findViewById(R.id.semesterSpinner)
        openResultsButton = findViewById(R.id.openResultsButton)

        closeButton.setOnClickListener { finish() }
        openResultsButton.setOnClickListener { openResultsScreen() }

        setupBranchSpinner()
        setupSemesterSpinner()
    }

    private fun setupBranchSpinner() {
        val branchLabels = SpecialDirectoryConfig.branchOptions.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, branchLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        branchSpinner.adapter = adapter
        branchSpinner.setSelection(0)
        selectedBranchId = SpecialDirectoryConfig.branchOptions.first().id
        selectedBranchLabel = SpecialDirectoryConfig.branchOptions.first().shortLabel
        branchSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedBranchId = SpecialDirectoryConfig.branchOptions[position].id
                selectedBranchLabel = SpecialDirectoryConfig.branchOptions[position].shortLabel
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupSemesterSpinner() {
        val semesterItems = listOf(getString(R.string.special_select_semester)) +
            (1..8).map { semester -> getString(R.string.special_semester_option, semester) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, semesterItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        semesterSpinner.adapter = adapter
        semesterSpinner.setSelection(0)
        semesterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSemester = if (position == 0) null else position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedSemester = null
            }
        }
    }

    private fun openResultsScreen() {
        val semester = selectedSemester
        if (semester == null) {
            Toast.makeText(this, R.string.special_select_semester_error, Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(
            Intent(this, SpecialSearchResultsActivity::class.java).apply {
                putExtra(SpecialSearchResultsActivity.EXTRA_BRANCH_ID, selectedBranchId)
                putExtra(SpecialSearchResultsActivity.EXTRA_BRANCH_LABEL, selectedBranchLabel)
                putExtra(SpecialSearchResultsActivity.EXTRA_SEMESTER, semester)
            },
        )
    }
}
