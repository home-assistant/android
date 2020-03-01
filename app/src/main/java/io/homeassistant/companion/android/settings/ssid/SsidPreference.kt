package io.homeassistant.companion.android.settings.ssid

import android.content.Context
import android.content.res.TypedArray
import android.text.TextUtils
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.DialogPreference
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.util.getAttribute
import javax.inject.Inject

class SsidPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = context.getAttribute(
        androidx.preference.R.attr.dialogPreferenceStyle,
        android.R.attr.dialogPreferenceStyle
    )
) : DialogPreference(context, attrs, defStyleAttr), Preference.OnPreferenceChangeListener {

    private companion object {
        private const val NO_MAX_LINES = -1
    }

    @Inject lateinit var ssidSummaryProvider: SsidSummaryProvider

    private var ssids: Set<String> = emptySet()

    private val summaryMaxLines: Int

    init {
        DaggerPresenterComponent
            .builder()
            .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.SsidPreference)
        try {
            summaryMaxLines = attributes.getInt(R.styleable.SsidPreference_summaryMaxLines, NO_MAX_LINES)
        } finally {
            attributes.recycle()
        }
        summaryProvider = ssidSummaryProvider
        onPreferenceChangeListener = this
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summaryView = holder.findViewById(android.R.id.summary) as? TextView
        if (summaryView != null && summaryMaxLines != NO_MAX_LINES) {
            summaryView.maxLines = summaryMaxLines
            summaryView.ellipsize = TextUtils.TruncateAt.END
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return emptySet<String>()
    }

    @Suppress("UNCHECKED_CAST")
    override fun onSetInitialValue(defaultValue: Any?) {
        setSsids(getPersistedStringSet(defaultValue as? Set<String>))
    }

    fun setSsids(ssids: Set<String>) {
        this.ssids = ssids
        persistStringSet(ssids)
        notifyChanged()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        return true
    }
}
