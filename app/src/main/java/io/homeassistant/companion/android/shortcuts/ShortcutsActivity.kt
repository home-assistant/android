package io.homeassistant.companion.android.shortcuts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.R

class ShortcutsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ShortcutsActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, ShortcutsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcuts)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager
            .beginTransaction()
            .add(R.id.content, ShortcutsFragment.newInstance())
            .commit()
    }
}
