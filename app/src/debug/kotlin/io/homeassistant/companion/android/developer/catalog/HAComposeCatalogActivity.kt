package io.homeassistant.companion.android.developer.catalog

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat

class HAComposeCatalogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()
        setContent { HAComposeCatalogScreen() }
    }
}
