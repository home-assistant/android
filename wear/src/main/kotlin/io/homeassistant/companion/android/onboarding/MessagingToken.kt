package io.homeassistant.companion.android.onboarding

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import timber.log.Timber

suspend fun getMessagingToken(): String {
    return try {
        FirebaseMessaging.getInstance().token.await()
    } catch (e: Exception) {
        Timber.e(e, "Issue getting token")
        ""
    }
}
