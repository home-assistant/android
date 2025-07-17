package io.homeassistant.companion.android.database

import android.content.res.AssetManager
import android.util.JsonReader
import android.util.NoSuchPropertyException
import androidx.annotation.WorkerThread
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Translation layer for IDs used by the old icondialog package to material icon names.
 */
class IconDialogCompat @Inject constructor(private val assets: AssetManager) {
    /**
     * Loads map of icon IDs to regular icon names.
     */
    @WorkerThread
    fun loadAllIcons(): Map<Int, String> {
        val inputStream = assets.open("mdi_id_map.json")
        return JsonReader(inputStream.reader()).use { reader ->
            val result = mutableMapOf<Int, String>()
            reader.beginObject()
            while (reader.hasNext()) {
                val iconName = reader.nextName()
                val iconId = reader.nextInt()
                result[iconId] = iconName
            }
            reader.endObject()
            result
        }
    }

    /**
     * Loads map of icon IDs to regular icon names in a background thread.
     */
    suspend fun loadAllIconsAsync() = coroutineScope {
        async(Dispatchers.IO) { loadAllIcons() }
    }

    suspend fun streamingIconLookup(iconId: Int): String {
        val iconName = withContext(Dispatchers.IO) {
            val inputStream = assets.open("mdi_id_map.json")
            JsonReader(inputStream.reader()).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val iconName = reader.nextName()
                    val id = reader.nextInt()
                    if (iconId == id) {
                        return@use iconName
                    }
                }
                reader.endObject()

                throw NoSuchPropertyException("ID $iconId is not valid")
            }
        }
        return iconName
    }
}
