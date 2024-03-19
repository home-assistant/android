package io.homeassistant.companion.android.barcode

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BarcodeScannerViewModel @Inject constructor(
    val app: Application
) : AndroidViewModel(app) {

    var hasPermission by mutableStateOf(false)
        private set

    init {
        checkPermission()
    }

    fun checkPermission() {
        hasPermission = ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}
