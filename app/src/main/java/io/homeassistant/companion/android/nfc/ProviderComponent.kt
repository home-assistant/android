package io.homeassistant.companion.android.nfc

import dagger.Component

@Component(dependencies = [AppComponent::class])
interface ProviderComponent {

    fun inject(activity: TagReaderActivity)
    fun inject(nfcEditFragment: NfcEditFragment)
}
