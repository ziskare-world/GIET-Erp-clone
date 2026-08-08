package com.example.gieterp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import kotlin.system.exitProcess

class DeviceBlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REASON = "extra_block_reason"

        fun start(context: Context, reason: String? = null) {
            val intent = Intent(context, DeviceBlockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_REASON, reason)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_device_block)
        SystemBarInsets.apply(findViewById(R.id.main))

        val reasonText = findViewById<TextView>(R.id.blockReason)
        val deviceIdText = findViewById<TextView>(R.id.deviceIdText)
        val exitButton = findViewById<Button>(R.id.buttonExitApp)

        val customReason = intent.getStringExtra(EXTRA_REASON)
        if (!customReason.isNullOrBlank() && customReason != "null") {
            reasonText.text = customReason
        }

        val installId = InstallIdentity.getOrCreate(this)
        deviceIdText.text = getString(R.string.device_blocked_id_label, installId)

        exitButton.setOnClickListener {
            finishAffinity()
            exitProcess(0)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Inescapable: prevent bypassing remote block
            }
        })
    }
}
