package io.homeassistant.companion.android.onboarding.manual

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface ManualSetupModule {

    @Binds
    fun manualSetupPresenter(manualSetupPresenterImpl: ManualSetupPresenterImpl): ManualSetupPresenter
}
