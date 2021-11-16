package io.homeassistant.companion.android.webview

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
abstract class WebviewModule {

    @Binds
    abstract fun webviewPresenter(webViewPresenterImpl: WebViewPresenterImpl): WebViewPresenter
}
