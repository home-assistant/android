package io.homeassistant.companion.android.util

import java.io.File
import java.util.concurrent.TimeUnit

object LogcatReader {

    fun saveLog(path: String): File {
        val command = arrayOf("logcat", "--pid=" + android.os.Process.myPid(), "-d", "-f", path)
        val process = Runtime.getRuntime().exec(command)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            process.waitFor(10, TimeUnit.SECONDS)
        } else process.waitFor()
        return File(path)
    }
}
