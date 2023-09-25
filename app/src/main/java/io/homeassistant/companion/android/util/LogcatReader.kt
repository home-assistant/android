package io.homeassistant.companion.android.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object LogcatReader {
    const val TAG = "LogcatReader"
    suspend fun readLog(): String = withContext(Dispatchers.IO) {
        val pid = android.os.Process.myPid()

        Log.d(TAG, "Read logcat for pid $pid")
        val log = StringBuilder()
        val pb = ProcessBuilder("logcat", "--pid=$pid", "-d")
        pb.redirectErrorStream(true)
        val process = pb.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            log.append(line + "\n")
        }
        process.waitFor()

        Log.d(TAG, "Done reading logcat for pid $pid")
        return@withContext log.toString()
    }
}
