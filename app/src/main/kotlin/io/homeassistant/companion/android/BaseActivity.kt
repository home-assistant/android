package io.homeassistant.companion.android

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.util.PermissionRequestMediator
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

@AndroidEntryPoint
open class BaseActivity : AppCompatActivity() {

    @Inject
    lateinit var permissionRequestMediator: PermissionRequestMediator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                permissionRequestMediator.eventFlow.collect { permissionToRequest ->
                    ActivityCompat.requestPermissions(
                        this@BaseActivity,
                        arrayOf(permissionToRequest),
                        permissionToRequest.hashCode().absoluteValue,
                    )
                }
            }
        }
    }
main
}
