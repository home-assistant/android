package io.homeassistant.companion.android.onboarding

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

suspend fun getRegistrationCode(): String {
    return FirebaseMessaging.getInstance().token.await()
}
