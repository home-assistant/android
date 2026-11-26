package androidx.biometric

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider

fun FragmentActivity.biometricClientCallback(): BiometricPrompt.AuthenticationCallback? = ViewModelProvider(this)[BiometricViewModel::class.java].clientCallback
