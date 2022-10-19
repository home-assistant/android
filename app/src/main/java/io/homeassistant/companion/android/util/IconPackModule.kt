package io.homeassistant.companion.android.util

import android.content.Context
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext

@Module
@InstallIn(ActivityComponent::class)
object IconPackModule {

    @Provides
    fun iconPack(@ActivityContext context: Context): IconPack {
        // Create an icon pack and load all drawables.
        val loader = IconPackLoader(context)
        return createMaterialDesignIconPack(loader).apply { loadDrawables(loader.drawableLoader) }
    }
}
