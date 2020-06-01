package io.homeassistant.companion.android.notifications

import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AbsListView
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.cursoradapter.widget.SimpleCursorAdapter
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.settings.SettingsActivity
import kotlinx.android.synthetic.main.activity_notifications.*

class NotificationsActivity : AppCompatActivity() {

    private var db: NotificationsDB? = null
    private lateinit var stream: ListView
    private lateinit var adapter: SimpleCursorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // TODO: Add menu item for settings (and launch settings activity from it!)

        loadDatabase()
        generateStream()
    }

    override fun onPause() {

        super.onPause()
        db?.close()
    }

    private fun loadDatabase() {

        db = NotificationsDB(this)

        try {

            db?.open()
        } catch (e: Exception) {

            e.printStackTrace()
        }
    }

    private fun generateStream() {

        stream = message_stream
        stream.emptyView = empty_stream

        val messages = db?.fetchMessages()

        val columns = arrayOf(NotificationsDB.KEY_TITLE, NotificationsDB.KEY_MESSAGE)
        val matrix = intArrayOf(R.id.text_title, R.id.text_message)

        adapter = SimpleCursorAdapter(this, R.layout.listrow_message_card, messages,
            columns, matrix, 0)

        stream.adapter = adapter
        stream.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE_MODAL
        stream.verticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT

        stream.setMultiChoiceModeListener(object : AbsListView.MultiChoiceModeListener {

            override fun onItemCheckedStateChanged(
                mode: ActionMode,
                position: Int,
                id: Long,
                checked: Boolean
            ) {

                mode.invalidate()
            }

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {

                mode.title = getString(R.string.cab_messages)
                mode.subtitle = (stream.checkedItemCount.toString() +
                        " " + getString(R.string.cab_selected))

                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {

                menu.clear()
                menuInflater.inflate(R.menu.menu_selected_notifications, menu)
                onCreateActionMode(mode, menu)
                supportActionBar?.hide()

                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {

                item.menuInfo

                when (item.itemId) {

                    R.id.delete_message -> {

                        val checked = stream.checkedItemPositions
                        val messageIdArray = ArrayList<String>()

                        for (i in 0 until checked.size()) {

                            if (checked.valueAt(i)) {

                                val cursor1 =
                                    stream.getItemAtPosition(checked.keyAt(i)) as Cursor
                                val messageDelete = cursor1
                                    .getString(cursor1.getColumnIndexOrThrow("_id"))
                                messageIdArray.add(messageDelete)
                            }
                        }

                        val messageIDs = messageIdArray
                            .toTypedArray()

                        deleteMessages(*messageIDs)

                        mode.finish()

                        return true
                    }

                    R.id.select_all -> {

                        if (stream.checkedItemCount != stream.count) {

                            for (selectAll in 0 until stream.count)
                                stream.setItemChecked(selectAll, true)
                        } else {

                            for (selectAll in 0 until stream.count)
                                stream.setItemChecked(selectAll, false)
                        }

                        return true
                    }
                }

                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {

                supportActionBar?.show()
            }
        })
    }

    private fun deleteMessages(vararg messageID: String) {

        var messageToDelete: String

        for (aGuidelineToDelete in messageID) {

            messageToDelete = aGuidelineToDelete

            try {

                db?.deleteMessage(messageToDelete)
            } catch (e: Exception) {

                e.printStackTrace()
            }
        }

        adapter.notifyDataSetChanged()
        generateStream()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.action_settings -> {

                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}
