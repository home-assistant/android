package io.homeassistant.companion.android.lock

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.SwitchPreference
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.settings.SettingsPresenter
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject

class LockActivity : AppCompatActivity(), LockView {

    companion object {

        fun newInstance(context: Context): Intent {
            return Intent(context, LockActivity::class.java)
        }

        fun biometric(
            set: Boolean = false,
            fragment: FragmentActivity,
            lockViewPresenter: LockPresenter? = null,
            switchLock: SwitchPreference? = null
        ) {

            val executor = ContextCompat.getMainExecutor(fragment)
            val biometricPrompt = BiometricPrompt(fragment, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        super.onAuthenticationError(errorCode, errString)
                        if (set)
                            switchLock?.isChecked = false
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        if (set)
                            switchLock?.isChecked = false
                    }

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)
                        if (!set)
                            lockViewPresenter?.onViewReady()
                    }
                })
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(fragment.resources.getString(R.string.biometric_title))
                .setSubtitle(fragment.resources.getString(R.string.biometric_message))
                .setDeviceCredentialAllowed(true)
                .build()

            biometricPrompt.authenticate(promptInfo)
        }

        @SuppressLint("ClickableViewAccessibility")
        fun pin(
            set: Boolean = false,
            fragment: FragmentActivity,
            lockViewPresenter: LockPresenter? = null,
            settingsPresenter: SettingsPresenter? = null,
            switchLock: SwitchPreference? = null
        ) {
            var pin: String
            var pintemp = ""
            var pinok = false
            val editImageArray: ArrayList<ImageView> = ArrayList(4)
            val editTextArray: ArrayList<EditText> = ArrayList(4)
            val inflater = fragment.layoutInflater
            val dialogLayout = inflater.inflate(R.layout.dialog_pin, null)
            val dialogBuilder = AlertDialog.Builder(fragment)
                .setView(dialogLayout)
            val dialog: AlertDialog = dialogBuilder.create()
            var numTemp = ""
            val title: TextView = dialogLayout.findViewById(R.id.pinTitle)
            val layout: LinearLayout = dialogLayout.findViewById(R.id.codeLayout)
            val circleLayout: LinearLayout = dialogLayout.findViewById((R.id.circleLayout))
            for (index in 0 until (layout.childCount)) {
                if (circleLayout.getChildAt(index) is ImageView) {
                    editImageArray.add(index, circleLayout.getChildAt(index) as ImageView)
                    editImageArray[index].setOnTouchListener { _, _ ->
                        if ((index != 0 && editImageArray[index - 1].tag == "set") || (index == 0)) {
                            for (i in index until (layout.childCount)) {
                                editImageArray[i].setImageResource(R.drawable.pin_circle)
                                editImageArray[i].tag = ""
                            }
                            editTextArray[index].requestFocus()
                            editTextArray[index].postDelayed({
                                val imm = editTextArray[index].context.getSystemService(
                                    AppCompatActivity.INPUT_METHOD_SERVICE
                                ) as InputMethodManager
                                imm.showSoftInput(editTextArray[index], InputMethodManager.SHOW_IMPLICIT)
                            }, 150)
                            editTextArray[index]
                                .setSelection(editTextArray[index].length())
                        }
                        false
                    }
                }
                val view: View = layout.getChildAt(index)
                if (view is EditText) {
                    editTextArray.add(index, view)
                    editTextArray[index].addTextChangedListener(object : TextWatcher {
                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                        ) {}

                        override fun afterTextChanged(s: Editable) {
                            (0 until editTextArray.size)
                                .forEach { i ->
                                    if (s === editTextArray[i].editableText) {
                                        if (s.isBlank()) {
                                            return
                                        }
                                        if (s.length >= 2) {
                                            val newTemp = s.toString().substring(s.length - 1, s.length)
                                            if (newTemp != numTemp)
                                                editTextArray[i].setText(newTemp)
                                            else
                                                editTextArray[i].setText(s.toString().substring(0, s.length - 1))
                                        } else if (i != editTextArray.size - 1) {
                                            editImageArray[i].setImageResource(R.drawable.pin_circle2)
                                            editImageArray[i].tag = "set"
                                            editTextArray[i + 1].requestFocus()
                                            editTextArray[i + 1].setSelection(editTextArray[i + 1].length())
                                            return
                                        } else {
                                            if (set) {
                                                editImageArray[i].setImageResource(R.drawable.pin_circle2)
                                                pin =
                                                    "${editTextArray[0].text}${editTextArray[1].text}${editTextArray[2].text}${editTextArray[3].text}"
                                                if (pintemp == "") {
                                                    title.text = fragment.resources.getString(R.string.pinscreen_title_second)
                                                    pintemp = pin
                                                    for (image in 0 until (layout.childCount)) {
                                                        editImageArray[image].setImageResource(R.drawable.pin_circle)
                                                        editImageArray[i].tag = ""
                                                    }
                                                    editTextArray[0].requestFocus()
                                                    editTextArray[0]
                                                        .setSelection(editTextArray[0].length())
                                                } else if (pin != pintemp) {
                                                    Toast.makeText(
                                                        fragment,
                                                        fragment.resources.getString(R.string.pinscreen_not_match),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    title.text = fragment.resources.getString(R.string.pinscreen_title)
                                                    pintemp = ""
                                                    for (image in 0 until (layout.childCount)) {
                                                        editImageArray[image].setImageResource(R.drawable.pin_circle)
                                                        editImageArray[i].tag = ""
                                                    }
                                                    editTextArray[0].requestFocus()
                                                    editTextArray[0]
                                                        .setSelection(editTextArray[0].length())
                                                } else {
                                                    pinok = true
                                                    settingsPresenter?.setPIN(pin)
                                                    dialog.dismiss()
                                                }
                                            } else {
                                                editImageArray[i].setImageResource(R.drawable.pin_circle2)
                                                editImageArray[i].tag = "set"
                                                pin = "${editTextArray[0].text}${editTextArray[1].text}${editTextArray[2].text}${editTextArray[3].text}"
                                                if (pin == lockViewPresenter?.getPIN()) {
                                                    lockViewPresenter?.onViewReady()
                                                    dialog.dismiss()
                                                } else {
                                                    Toast.makeText(
                                                        fragment,
                                                        fragment.resources.getString(R.string.pinscreen_bad_pin),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    for (image in 0 until (layout.childCount)) {
                                                        editImageArray[image].setImageResource(R.drawable.pin_circle)
                                                        editImageArray[i].tag = ""
                                                    }
                                                    editTextArray[0].requestFocus()
                                                    editTextArray[0]
                                                        .setSelection(editTextArray[0].length())
                                                }
                                            }
                                        }
                                    }
                                }
                        }

                        override fun beforeTextChanged(
                            s: CharSequence,
                            start: Int,
                            count: Int,
                            after: Int
                        ) {
                            numTemp = s.toString()
                        }
                    })
                    editTextArray[index].setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                            if (index != 0) {
                                editTextArray[index - 1].requestFocus()
                                editImageArray[index - 1].setImageResource(R.drawable.pin_circle)
                                editImageArray[index - 1].tag = ""
                                editTextArray[index - 1]
                                    .setSelection(editTextArray[index - 1].length())
                            }
                        }
                        false
                    }
                }
            }

            editTextArray[0].requestFocus()

            dialog.setOnDismissListener {
                if (!pinok)
                    if (set)
                        switchLock?.isChecked = false
            }

            dialog.show()
        }
    }

    @Inject
    lateinit var presenter: LockPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        if (presenter.isLockEnabled()) {
            val button = findViewById<ImageView>(R.id.unlockButton)
            button.setOnClickListener {
                if (BiometricManager.from(this).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
                    biometric(
                        fragment = this,
                        lockViewPresenter = presenter
                    )
                } else
                    pin(
                        fragment = this,
                        lockViewPresenter = presenter
                    )
            }
        }
    }

    override fun displayWebview() {
        startActivity(WebViewActivity.newInstance(this))
        finish()
    }
}
