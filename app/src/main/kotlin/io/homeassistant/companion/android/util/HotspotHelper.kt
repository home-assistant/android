package io.homeassistant.companion.android.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import timber.log.Timber
import java.util.concurrent.Executor

/**
 * Helper class for managing Wi-Fi hotspot functionality.
 * Only supported on Android 11 (API 30) and above.
 */
class HotspotHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TETHERING_WIFI = 0
        private const val METHOD_TETHERING_MANAGER = "TetheringManager"
        // private const val METHOD_CONNECTIVITY_MANAGER = "ConnectivityManager"
    }
    
    // Executor implementation to run on the main thread
    private val mainExecutor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }

    /**
     * Enables the Wi-Fi hotspot.
     * Only works on Android 11+.
     * 
     * @return true if operation was successful
     */
    fun turnOnHotspot(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Timber.i("Hotspot control not supported on Android versions below 11 (API 30)")
            return false
        }

        try {
            return enableHotspot()
        } catch (e: Exception) {
            Timber.e(e, "Failed to turn on hotspot")
            return false
        }
    }

    /**
     * Disables the Wi-Fi hotspot.
     * Only works on Android 11+.
     * 
     * @return true if operation was successful
     */
    fun turnOffHotspot(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Timber.i("Hotspot control not supported on Android versions below 11 (API 30)")
            return false
        }
        
        try {
            return disableHotspot()
        } catch (e: Exception) {
            Timber.e(e, "Failed to turn off hotspot")
            return false
        }
    }

    // API 30+ needed for TetheringManager
    @RequiresApi(Build.VERSION_CODES.R)
    private fun enableHotspot(): Boolean {
        // Try TetheringManager
        // Support least Android 11(API 30) Code.R
        if (enableHotspotWithTetheringManager()) {
            Timber.i("Hotspot enabled successfully using $METHOD_TETHERING_MANAGER")
            return true
        }

        // TODO: implement using ConnectivityManager as fallback

        Timber.e("Failed to enable hotspot: no viable method found")
        return false
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun disableHotspot(): Boolean {
        // Try TetheringManager
        // Support least Android 11(API 30) Code.R
        if (disableHotspotWithTetheringManager()) {
            Timber.i("Hotspot disabled successfully using $METHOD_TETHERING_MANAGER")
            return true
        }

        // TODO: implement using ConnectivityManager as fallback
        
        Timber.e("Failed to disable hotspot: no viable method found")
        return false
    }

    /**
     * Attempts to enable hotspot using TetheringManager via reflection.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun enableHotspotWithTetheringManager(): Boolean {
        try {
            val tetheringManagerClass = Class.forName("android.net.TetheringManager")
            val tetheringManager = context.getSystemService(tetheringManagerClass) ?: return false
            
            // Look for appropriate method
            for (method in tetheringManager.javaClass.methods) {
                if (method.name == "startTethering") {
                    try {
                        val callbackClass = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
                        
                        // Try with TetheringRequest (preferred on newer devices)
                        val tetheringRequestClass = Class.forName("android.net.TetheringManager\$TetheringRequest")
                        val builderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
                        val builder = builderClass.getConstructor(Int::class.java).newInstance(TETHERING_WIFI)
                        val request = builderClass.getMethod("build").invoke(builder)

                        val startMethod = tetheringManager.javaClass.getMethod(
                            "startTethering",
                            tetheringRequestClass,
                            Executor::class.java,
                            callbackClass
                        )

                        val callbackInstance = createCallbackInstance(callbackClass)

                        Timber.d("Enabling hotspot with TetheringRequest approach")
                        startMethod.invoke(tetheringManager, request, mainExecutor, callbackInstance)
                        return true
                    }
                    catch (e: Exception) {
                        Timber.e(e, "Error invoking TetheringManager.startTethering")
                    }
                }
            }
            Timber.w("No suitable TetheringManager.startTethering method found")
            return false
        } catch (e: Exception) {
            Timber.e(e, "Error accessing TetheringManager")
            return false
        }
    }
    
    /**
     * Creates appropriate callback instance through reflection.
     * Handles both interface and abstract class scenarios.
     */
    private fun createCallbackInstance(callbackClass: Class<*>): Any {
        try {
            if (callbackClass.isInterface) {
                // For interfaces, use Proxy
                return java.lang.reflect.Proxy.newProxyInstance(
                    callbackClass.classLoader,
                    arrayOf(callbackClass)
                ) { _, method, _ ->
                    when (method.name) {
                        "onTetheringStarted" -> {
                            Timber.d("Callback: Tethering started successfully")
                            null
                        }
                        // this also causes when tethering already started
                        "onTetheringFailed" -> {
                            Timber.e("Callback: Tethering failed to start")
                            null
                        }
                        else -> null
                    }
                }
            } else {
                // For abstract classes, create instance via constructor
                val constructor = callbackClass.getDeclaredConstructor()
                constructor.isAccessible = true
                return constructor.newInstance()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create callback instance")
            throw e
        }
    }

    /**
     * Attempts to disable hotspot using TetheringManager via reflection.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun disableHotspotWithTetheringManager(): Boolean {
        try {
            val tetheringManagerClass = Class.forName("android.net.TetheringManager")
            val tetheringManager = context.getSystemService(tetheringManagerClass) ?: return false
            
            // Look for stopTethering method
            for (method in tetheringManager.javaClass.methods) {
                if (method.name == "stopTethering" && 
                    method.parameterTypes.size == 1 && 
                    method.parameterTypes[0] == Int::class.java) {
                    try {
                        Timber.d("Disabling hotspot with TetheringManager.stopTethering")
                        method.invoke(tetheringManager, TETHERING_WIFI)
                        return true
                    } catch (e: Exception) {
                        Timber.e(e, "Error invoking TetheringManager.stopTethering")
                    }
                }
            }
            Timber.w("No suitable TetheringManager.stopTethering method found")
            return false
        } catch (e: Exception) {
            Timber.e(e, "Error accessing TetheringManager")
            return false
        }
    }
}
