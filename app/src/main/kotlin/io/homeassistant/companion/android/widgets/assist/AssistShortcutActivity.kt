package io.homeassistant.companion.android.widgets.assist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import java.util.UUID

@AndroidEntryPoint
class AssistShortcutActivity : BaseActivity() {

    companion object {
        const val SHORTCUT_PREFIX = ".ha_assist_"
    }

    val viewModel: AssistShortcutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HomeAssistantAppTheme {
                AssistShortcutView(
                    selectedServerId = viewModel.serverId,
                    servers = viewModel.servers,
                    supported = viewModel.supported,
                    pipelines = viewModel.pipelines,
                    onSetServer = viewModel::setServer,
                    onSubmit = this::setShortcutAndFinish,
                )
            }
        }
    }

    private fun setShortcutAndFinish(name: String, serverId: Int, pipelineId: String?, startListening: Boolean) {
        val assistIntent = AssistActivity.newInstance(
            context = this,
            serverId = serverId,
            pipelineId = pipelineId,
            startListening = startListening,
            fromFrontend = false,
        ).apply {
            action = Intent.ACTION_VIEW
        }
        val shortcutInfo = ShortcutInfoCompat.Builder(this, "$SHORTCUT_PREFIX${UUID.randomUUID()}")
            .setIntent(assistIntent)
            .setShortLabel(name)
            .setLongLabel(name)
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_assist_launcher))
            .build()
        val resultIntent = ShortcutManagerCompat.createShortcutResultIntent(this, shortcutInfo)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
