package io.homeassistant.companion.android.assist

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.assist.ui.AssistSheetView
import io.homeassistant.companion.android.common.assist.AssistViewModelBase
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.webview.WebViewActivity

@AndroidEntryPoint
class AssistActivity : BaseActivity() {

    private val viewModel: AssistViewModel by viewModels()

    companion object {
        const val TAG = "AssistActivity"

        private const val EXTRA_SERVER = "server"
        private const val EXTRA_PIPELINE = "pipeline"
        private const val EXTRA_START_LISTENING = "start_listening"
        private const val EXTRA_FROM_FRONTEND = "from_frontend"

        fun newInstance(
            context: Context,
            serverId: Int = -1,
            pipelineId: String? = null,
            startListening: Boolean = true,
            fromFrontend: Boolean = true
        ): Intent {
            return Intent(context, AssistActivity::class.java).apply {
                putExtra(EXTRA_SERVER, serverId)
                putExtra(EXTRA_PIPELINE, pipelineId)
                putExtra(EXTRA_START_LISTENING, startListening)
                putExtra(EXTRA_FROM_FRONTEND, fromFrontend)
            }
        }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        { viewModel.onPermissionResult(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        } // else handled by manifest attribute
        val isLocked = getSystemService<KeyguardManager>()?.isKeyguardLocked ?: false
        if (isLocked) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        }

        if (savedInstanceState == null) {
            viewModel.onCreate(
                hasPermission = hasRecordingPermission(),
                serverId = if (intent.hasExtra(EXTRA_SERVER)) {
                    intent.getIntExtra(EXTRA_SERVER, ServerManager.SERVER_ID_ACTIVE)
                } else {
                    null
                },
                pipelineId = if (intent.hasExtra(EXTRA_PIPELINE)) {
                    intent.getStringExtra(EXTRA_PIPELINE) ?: AssistViewModelBase.PIPELINE_LAST_USED
                } else {
                    AssistViewModelBase.PIPELINE_LAST_USED
                },
                startListening = if (intent.hasExtra(EXTRA_START_LISTENING)) {
                    intent.getBooleanExtra(EXTRA_START_LISTENING, true)
                } else {
                    null
                }
            )
        }

        val fromFrontend = intent.getBooleanExtra(EXTRA_FROM_FRONTEND, false)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MdcTheme {
                val systemUiController = rememberSystemUiController()
                val useDarkIcons = MaterialTheme.colors.isLight
                SideEffect {
                    systemUiController.setSystemBarsColor(Color.Transparent, darkIcons = useDarkIcons)
                }

                AssistSheetView(
                    conversation = viewModel.conversation,
                    pipelines = viewModel.pipelines,
                    inputMode = viewModel.inputMode,
                    fromFrontend = fromFrontend,
                    currentPipeline = viewModel.currentPipeline,
                    onSelectPipeline = viewModel::changePipeline,
                    onManagePipelines =
                    if (fromFrontend && viewModel.userCanManagePipelines()) {
                        {
                            startActivity(
                                WebViewActivity.newInstance(
                                    this,
                                    "config/voice-assistants/assistants"
                                ).apply {
                                    flags += Intent.FLAG_ACTIVITY_NEW_TASK // Delivers data in onNewIntent
                                }
                            )
                            finish()
                        }
                    } else {
                        null
                    },
                    onChangeInput = viewModel::onChangeInput,
                    onTextInput = viewModel::onTextInput,
                    onMicrophoneInput = viewModel::onMicrophoneInput,
                    onHide = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setPermissionInfo(hasRecordingPermission()) { requestPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        this.intent = intent
        viewModel.onNewIntent(intent)
    }

    private fun hasRecordingPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
