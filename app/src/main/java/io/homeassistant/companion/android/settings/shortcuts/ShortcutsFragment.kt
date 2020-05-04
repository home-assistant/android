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
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.Panel
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

        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
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
                "calendar" -> context!!.getString(R.string.calendar)
                "config" -> context!!.getString(R.string.config)
                "developer_tools" -> context!!.getString(R.string.developer_tools)
                "history" -> context!!.getString(R.string.history)
                "logbook" -> context!!.getString(R.string.logbook)
                "mailbox" -> context!!.getString(R.string.mailbox)
                "map" -> context!!.getString(R.string.map)
                "profile" -> context!!.getString(R.string.profile)
                "shopping_list" -> context!!.getString(R.string.shopping_list)
                "states" -> context!!.getString(R.string.states)
                else -> panel.title
            }
            panel
        }

        recyclerViewAdapter =
            ShortcutsRecyclerViewAdapter(panels.toList(), context!!) { onCreateShortcut(it) }

        return inflater.inflate(R.layout.fragment_shortcuts, container, false).apply {
            findViewById<RecyclerView>(R.id.recycler_view_shortcuts)?.apply {
                adapter = recyclerViewAdapter
            }
        }
    }

    private fun onCreateShortcut(panel: Panel) {
        val shortcutManager =
            context!!.getSystemService(ShortcutManager::class.java)
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
                    .setIntent(
                        WebViewActivity.newInstance(
                            context!!,
                            panel.url_path
                        ).apply {
                            this.action = Intent.ACTION_VIEW
                        }
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
