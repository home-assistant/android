package io.homeassistant.companion.android.onboarding.manual

enum class URLError{
    NO_PROTOCOL
}


interface ManualSetupView {

    fun urlSaved()

    fun displayUrlError()

}
