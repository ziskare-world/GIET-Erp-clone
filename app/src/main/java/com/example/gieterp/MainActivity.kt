package com.example.gieterp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val SPECIAL_TRIGGER_CODE = "*2306#"
        private const val SPECIAL_PASSWORD = "2005"
    }

    private lateinit var button: ImageButton
    private lateinit var rollnoInput: EditText
    private lateinit var versionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        AttendancePushTokenSync.requestNotificationPermissionIfNeeded(this)

        val sharedPref = getSharedPreferences(AppSession.PREFERENCES_NAME, MODE_PRIVATE)
        val storedRollNo = sharedPref.getString(AppSession.KEY_ROLL_NO, null)
        if (!storedRollNo.isNullOrEmpty()) {
            val dashboardIntent = Intent(this, MainActivity2::class.java)
            AttendanceNotificationIntents.copyExtras(intent, dashboardIntent)
            startActivity(dashboardIntent)
            finish()
            return
        }

        SystemBarInsets.apply(findViewById(R.id.main))

        button = findViewById(R.id.submit)
        rollnoInput = findViewById(R.id.rollno)
        versionText = findViewById(R.id.versionText)
        versionText.text = getString(R.string.app_name_with_version, getString(R.string.giet_erp), AppVersion.name(this))

        button.setOnClickListener {
            val rollNo = rollnoInput.text.toString().trim()
            if (rollNo.isEmpty()) {
                Toast.makeText(this, R.string.enter_rollno, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (rollNo == SPECIAL_TRIGGER_CODE) {
                showSpecialAccessDialog()
                return@setOnClickListener
            }

            sharedPref.edit {
                putString(AppSession.KEY_ROLL_NO, rollNo)
            }

            RemoteControlService.ensureInstallRegistered(applicationContext, rollNo)
            AttendancePushTokenSync.syncCurrentTokenIfPossible(applicationContext)

            val dashboardIntent = Intent(this, MainActivity2::class.java)
            AttendanceNotificationIntents.copyExtras(intent, dashboardIntent)
            startActivity(dashboardIntent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        AppUpdateChecker.checkForUpdates(this)
    }

    private fun showSpecialAccessDialog() {
        val passwordInput = EditText(this).apply {
            hint = getString(R.string.secret_password_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            background = getDrawable(R.drawable.bg_input_field)
            setPadding(32, 24, 32, 24)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.secret_password_title)
            .setMessage(R.string.secret_password_message)
            .setView(passwordInput)
            .setPositiveButton(R.string.submit) { _, _ ->
                val enteredPassword = passwordInput.text.toString()
                if (enteredPassword == SPECIAL_PASSWORD) {
                    startActivity(Intent(this, SpecialAccessActivity::class.java))
                } else {
                    Toast.makeText(this, R.string.secret_password_invalid, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
