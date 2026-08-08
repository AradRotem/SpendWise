package com.aradrotem.spendwise.data.auth

import android.content.Context
import com.google.firebase.FirebaseApp

// Whether a real Firebase configuration (google-services.json, applied at build time via the
// conditionally-applied google-services plugin) is present on this build. Every class that would
// otherwise touch FirebaseAuth/FirebaseFirestore checks this first, so a build/install without
// the config file never crashes - it just surfaces a "Firebase setup required" state instead of
// faking success. See FirebaseAuthRepository / FirebaseUnavailableAuthRepository.
object FirebaseAvailability {

    @Volatile
    private var cached: Boolean? = null

    fun isConfigured(context: Context): Boolean {
        cached?.let { return it }
        // FirebaseApp.initializeApp returns null (rather than throwing) when no default
        // FirebaseOptions could be determined from resources - i.e. no google-services.json was
        // present at build time to generate them.
        val configured = try {
            FirebaseApp.initializeApp(context.applicationContext) != null
        } catch (e: IllegalStateException) {
            false
        }
        cached = configured
        return configured
    }
}
