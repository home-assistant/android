package io.homeassistant.companion.android.settings.manageinstances

interface ManageInstanceView {
    fun showInstanceList(instances: List<String>)
    fun launchInstance()
    fun addNewInstance()
}