package io.homeassistant.companion.android.wear

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.PresenterScope
import io.homeassistant.companion.android.wear.action.ActionActivity
import io.homeassistant.companion.android.wear.actions.ActionsFragment
import io.homeassistant.companion.android.wear.background.BackgroundModule
import io.homeassistant.companion.android.wear.launch.LaunchActivity
import io.homeassistant.companion.android.wear.navigation.NavigationActivity
import io.homeassistant.companion.android.wear.settings.SettingsFragment

@PresenterScope
@Component(
    dependencies = [AppComponent::class],
    modules = [PresenterModule::class, BackgroundModule::class]
)
interface PresenterComponent {

    @Component.Factory
    interface Factory {
        fun create(
            appComponent: AppComponent,
            presenterModule: PresenterModule
        ): PresenterComponent
    }

    fun inject(activity: LaunchActivity)
    fun inject(activity: NavigationActivity)
    fun inject(fragment: ActionsFragment)
    fun inject(activity: ActionActivity)
    fun inject(fragment: SettingsFragment)
}
