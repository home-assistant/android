package io.homeassistant.companion.android.common.dagger

import dagger.Component
import javax.inject.Singleton

@Singleton
@Component
interface AppComponent {

    @Component.Factory
    interface Factory {
        fun create(): AppComponent
    }

}
