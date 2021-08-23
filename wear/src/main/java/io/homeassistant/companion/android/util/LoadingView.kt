package io.homeassistant.companion.android.util

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import io.homeassistant.companion.android.R

class LoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        inflate(context, R.layout.view_loading, this)

        val loadingText: TextView = findViewById(R.id.loading_text)

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.LoadingView)
        loadingText.text = attributes.getString(R.styleable.LoadingView_loading_text)
        attributes.recycle()
    }
}
