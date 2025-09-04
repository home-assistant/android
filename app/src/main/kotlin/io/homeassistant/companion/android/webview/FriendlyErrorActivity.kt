package io.homeassistant.companion.android.webview

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.databinding.ActivityFriendlyErrorBinding

class FriendlyErrorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendlyErrorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendlyErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val friendlyMessage = intent.getStringExtra(EXTRA_FRIENDLY).orEmpty()
        val technicalDetails = intent.getStringExtra(EXTRA_TECHNICAL).orEmpty()

        binding.friendlyText.text = friendlyMessage
        binding.technicalText.text = technicalDetails

        binding.advancedButton.setOnClickListener {
            binding.technicalText.visibility =
                if (binding.technicalText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    companion object {
        const val EXTRA_FRIENDLY = "friendlyMessage"
        const val EXTRA_TECHNICAL = "technicalDetails"
    }
}
