package com.example.gieterp

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

object InstallIdentity {
    fun getOrCreate(context: Context): String {
        val preferences = context.getSharedPreferences(AppSession.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val existing = preferences.getString(AppSession.KEY_INSTALL_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val generated = UUID.randomUUID().toString()
        preferences.edit {
            putString(AppSession.KEY_INSTALL_ID, generated)
        }
        return generated
    }
}
