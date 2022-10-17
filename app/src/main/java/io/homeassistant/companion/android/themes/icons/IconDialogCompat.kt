package io.homeassistant.companion.android.themes.icons

import android.content.res.AssetManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import javax.inject.Inject

class IconDialogCompat @Inject constructor(
    private val assets: AssetManager
) {
    @OptIn(DelicateCoroutinesApi::class)
    private val dialogIdToMetaAsync = GlobalScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        val meta = IconMeta.loadAll(assets)
        meta.associateBy { it.dialoglibId }
    }

    /**
     * Looks up the icon metadata corresponding to the stored icon.
     */
    suspend fun getDialogIconMeta(iconId: Int): IconMeta? {
        val dialogIdToMeta = dialogIdToMetaAsync.await()
        return dialogIdToMeta[iconId]
    }
}
