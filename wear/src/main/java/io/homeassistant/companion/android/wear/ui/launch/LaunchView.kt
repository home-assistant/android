package io.homeassistant.companion.android.wear.ui.launch

import android.app.Activity
import com.google.android.gms.wearable.Node

interface LaunchView {
    fun showProgressBar(show: Boolean)
    fun setStateInfo(message: Int?)
    fun showActionButton(message: Int?, icon: Int? = null, action: (Activity.() -> Unit)? = null)

    suspend fun getNodeWithInstalledApp(): Node?
    suspend fun sendMessage(nodeId: String)

    fun displayUnreachable()
    fun displayInactiveSession()
    fun displayNextScreen()

}