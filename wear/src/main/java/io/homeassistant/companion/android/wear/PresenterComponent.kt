package io.homeassistant.companion.android.wear

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.DomainComponent
import io.homeassistant.companion.android.common.dagger.PresenterScope
import io.homeassistant.companion.android.wear.actions.ActionsFragment
import io.homeassistant.companion.android.wear.background.BackgroundModule
import io.homeassistant.companion.android.wear.create.CreateActionActivity
import io.homeassistant.companion.android.wear.launch.LaunchActivity
import io.homeassistant.companion.android.wear.navigation.NavigationActivity
import io.homeassistant.companion.android.wear.settings.SettingsFragment

@PresenterScope
@Component(
    dependencies = [AppComponent::class, DomainComponent::class],
    modules = [PresenterModule::class, BackgroundModule::class]
)
interface PresenterComponent {

    @Component.Factory
    interface Factory {
        fun create(
            appComponent: AppComponent,
            domainComponent: DomainComponent,
            presenterModule: PresenterModule,
            @BindsInstance context: Context
        ): PresenterComponent
    }

    fun inject(activity: LaunchActivity)
    fun inject(activity: NavigationActivity)
    fun inject(fragment: ActionsFragment)
    fun inject(activity: CreateActionActivity)
    fun inject(fragment: SettingsFragment)

}
