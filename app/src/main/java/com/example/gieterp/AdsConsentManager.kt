package com.example.gieterp

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

class AdsConsentManager private constructor(context: Context) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context.applicationContext)

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gatherConsent(
        activity: AppCompatActivity,
        onConsentStateUpdated: () -> Unit,
    ) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                onConsentStateUpdated()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    onConsentStateUpdated()
                }
            },
            {
                onConsentStateUpdated()
            },
        )
    }

    fun showPrivacyOptionsForm(
        activity: AppCompatActivity,
        onDismissed: (FormError?) -> Unit,
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, onDismissed)
    }

    companion object {
        @Volatile
        private var instance: AdsConsentManager? = null

        fun getInstance(context: Context): AdsConsentManager {
            return instance ?: synchronized(this) {
                instance ?: AdsConsentManager(context).also { instance = it }
            }
        }
    }
}
