package io.homeassistant.companion.android.nfc

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface ProviderComponent {

    fun inject(activity: NfcActivity)
    fun inject(nfcEditFragment: NfcEditFragment)
}
