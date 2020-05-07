package io.homeassistant.companion.android.settings.shortcuts

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.databinding.FragmentShortcutsBinding
import io.homeassistant.companion.android.domain.integration.Panel
import io.homeassistant.companion.android.util.appComponent
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
class ShortcutsFragment : Fragment(), ShortcutsView {

    companion object {
        fun newInstance(): ShortcutsFragment {
            return ShortcutsFragment()
        }
    }

    @Inject
    lateinit var presenter: ShortcutsPresenter

    private lateinit var recyclerViewAdapter: ShortcutsRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerPresenterComponent.factory()
            .create(appComponent, PresenterModule(this))
            .inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val panels = presenter.getPanels().filter { panel ->
            !panel.title.isNullOrEmpty() && panel.title !== "lovelace" && panel.title !== "profile"
        }.sortedBy { panel -> panel.title }.map { panel ->
            panel.title_localized = when (panel.title) {
                "calendar" -> getString(R.string.calendar)
                "config" -> getString(R.string.config)
                "developer_tools" -> getString(R.string.developer_tools)
                "history" -> getString(R.string.history)
                "logbook" -> getString(R.string.logbook)
                "mailbox" -> getString(R.string.mailbox)
                "map" -> getString(R.string.map)
                "profile" -> getString(R.string.profile)
                "shopping_list" -> getString(R.string.shopping_list)
                "states" -> getString(R.string.states)
                else -> panel.title
            }
            panel
        }

        recyclerViewAdapter =
            ShortcutsRecyclerViewAdapter(panels.toList(), requireContext()) { onCreateShortcut(it) }

        val binding = FragmentShortcutsBinding.inflate(inflater, container, false)
        binding.recyclerViewShortcuts.adapter = recyclerViewAdapter
        return binding.root
    }

    private fun onCreateShortcut(panel: Panel) {
        val shortcutManager =
            requireContext().getSystemService(ShortcutManager::class.java)
        if (shortcutManager!!.isRequestPinShortcutSupported) {
            val pinShortcutInfo =
                ShortcutInfo.Builder(
                    context,
                    panel.title
                )
                    .setShortLabel(panel.title_localized!!)
                    .setLongLabel(panel.title_localized!!)
                    .setIcon(
                        Icon.createWithResource(
                            context,
                            R.drawable.app_icon
                        )
                    )
                    .setIntent(WebViewActivity.newInstance(requireContext(), panel.url_path).setAction(Intent.ACTION_VIEW)
                    )
                    .build()
            val pinnedShortcutCallbackIntent =
                shortcutManager.createShortcutResultIntent(
                    pinShortcutInfo
                )
            val successCallback =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    pinnedShortcutCallbackIntent,
                    0
                )
            shortcutManager.requestPinShortcut(
                pinShortcutInfo,
                successCallback.intentSender
            )
        }
    }
}
