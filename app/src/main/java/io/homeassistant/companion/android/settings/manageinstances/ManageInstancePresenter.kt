package io.homeassistant.companion.android.settings.manageinstances

interface ManageInstancePresenter {

    fun getInstances()

    fun switchToInstance(url: String)

    fun addNewInstance()

    fun deleteInstance(url: String)
}