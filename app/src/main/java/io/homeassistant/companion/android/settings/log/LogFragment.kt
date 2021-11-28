package io.homeassistant.companion.android.settings.log

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.util.LogcatReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import io.homeassistant.companion.android.common.R as commonR

class LogFragment : Fragment() {

    private var currentLog = ""

    companion object {
        private const val TAG = "LogFragment"
        private const val SHARE_LOGS_REQUEST_CODE = 2
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.share_log -> {
                val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(requireContext(), permission) === PackageManager.PERMISSION_DENIED) {
                    Log.d(TAG, "Click on share logs without WRITE_EXTERNAL_STORAGE permission")
                    AlertDialog.Builder(requireActivity())
                        .setTitle(getString(commonR.string.share_logs))
                        .setMessage(getString(commonR.string.share_logs_perm_message))
                        .setPositiveButton(commonR.string.confirm_positive) { _, _ ->
                            Log.i(TAG, "Request WRITE_EXTERNAL_STORAGE permission, to create log file")
                            requestPermissions(arrayOf(permission), SHARE_LOGS_REQUEST_CODE)
                        }
                        .setNegativeButton(commonR.string.confirm_negative) { _, _ ->
                            Log.w(TAG, "User cancel request for WRITE_EXTERNAL_STORAGE permission")
                            // Do nothing
                        }.show()
                } else {
                    shareLog()
                }
                return true
            }
            R.id.refresh_log -> {
                refreshLog()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        refreshLog()
    }

    private fun refreshLog() = lifecycleScope.launch(Dispatchers.Main) {
        if (view != null && activity != null) {
            val logTextView = requireView().findViewById<TextView>(R.id.logTextView)
            val toolbar = requireActivity().findViewById<Toolbar>(R.id.toolbar)

            toolbar.menu.setGroupVisible(R.id.log_toolbar_group, false)
            showHideLogLoader(true)

            // Runs with Dispatcher IO
            currentLog = LogcatReader.readLog()

            logTextView?.text = currentLog
            toolbar.menu.setGroupVisible(R.id.log_toolbar_group, true)
            showHideLogLoader(false)
            (view?.findViewById<ScrollView>(R.id.logScrollview))?.apply {
                post { fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SHARE_LOGS_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            shareLog()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_log, container, false)
    }

    private fun shareLog() {
        AlertDialog.Builder(requireActivity())
            .setTitle(getString(commonR.string.share_logs))
            .setMessage(getString(commonR.string.share_logs_sens_message))
            .setPositiveButton(commonR.string.confirm_positive) { _, _ ->
                Log.d(TAG, "User want to share log")
                val c = Calendar.getInstance()
                val year = c.get(Calendar.YEAR)
                val month = c.get(Calendar.MONTH)
                val day = c.get(Calendar.DAY_OF_MONTH)
                val hour = c.get(Calendar.HOUR_OF_DAY)
                val minute = c.get(Calendar.MINUTE)
                val second = c.get(Calendar.SECOND)

                val path = requireContext().externalCacheDir?.absolutePath + "/logs"
                val fLogFilePath = File(path)
                // First clear all logs which was created before
                fLogFilePath.deleteRecursively()
                // Recreate log dir
                fLogFilePath.mkdir()

                val filePathWithoutExt = path + "/homeassistant_companion_log_$month-$day-$year" + "_" + "$hour-$minute-$second"
                val logFilePath = "$filePathWithoutExt.txt"

                Log.i(TAG, "Create log file to: $logFilePath")

                val fLogFile = File(logFilePath)
                fLogFile.appendText(currentLog)

                if (fLogFile.exists()) {

                    val uriToLog: Uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".provider", fLogFile)

                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uriToLog)
                        type = "text/plain"
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }

                    val shareIntent = Intent.createChooser(sendIntent, null).apply {
                        // Lets exclude github app, because github doesn't support sharing text files (only images)
                        // Also no issue template will be used
                        val excludedComponents = getExcludedComponentsForPackageName(sendIntent, arrayOf("com.github.android"))
                        if (excludedComponents.size > 0) {
                            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excludedComponents.toTypedArray())
                        }
                    }

                    val packageManager: PackageManager = requireContext().packageManager
                    if (shareIntent.resolveActivity(packageManager) != null) {
                        Log.i(TAG, "Open share dialog with log file")
                        startActivity(shareIntent)
                    } else {
                        Log.e(TAG, "Cannot open share dialog, because no app can receive the mime type text/plain")
                    }
                } else {
                    Log.e(TAG, "Could not open share dialog, because log file does not exist.")
                    Toast.makeText(requireContext(), getString(commonR.string.log_file_not_created), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(commonR.string.confirm_negative) { _, _ ->
                Log.w(TAG, "User don't want to share the log")
                // Do nothing
            }.show()
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.log)
    }

    private fun getExcludedComponentsForPackageName(sendIntent: Intent, packageNames: Array<String>): ArrayList<ComponentName> {
        val excludedComponents = ArrayList<ComponentName>()
        val resInfos = requireContext().packageManager.queryIntentActivities(sendIntent, 0)
        for (resInfo in resInfos) {
            val packageName = resInfo.activityInfo.packageName
            val name = resInfo.activityInfo.name
            if (packageNames.contains(packageName)) {
                excludedComponents.add(ComponentName(packageName, name))
            }
        }
        return excludedComponents
    }

    private fun showHideLogLoader(show: Boolean) {
        if (view != null) {
            val logLoader = requireView().findViewById<LinearLayout>(R.id.logLoader)
            val logScrollView = requireView().findViewById<ScrollView>(R.id.logScrollview)

            logScrollView.isGone = show
            logLoader.isVisible = show
        }
    }
}
