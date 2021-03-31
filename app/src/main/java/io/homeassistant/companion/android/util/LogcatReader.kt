package io.homeassistant.companion.android.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object LogcatReader {
    fun readLog(): String {
        val command = arrayOf("logcat", "--pid=" + android.os.Process.myPid(), "-d")
        val process = Runtime.getRuntime().exec(command)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            process.waitFor(10, TimeUnit.SECONDS)
        } else process.waitFor()

        val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

        val log = StringBuilder()
        var line: String? = ""
        while (bufferedReader.readLine().also { line = it } != null) {
            log.append(line + "\n")
        }
        return log.toString()
    }
}
