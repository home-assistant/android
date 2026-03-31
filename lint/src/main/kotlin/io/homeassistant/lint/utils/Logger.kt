package io.homeassistant.lint.utils

private const val RESET_ANSI = "\u001B[0m"
private const val RED_ANSI = "\u001B[31m"
private const val YELLOW_ANSI = "\u001B[33m"
private const val WHITE_ANSI = "\u001B[37m"

object Logger {
    fun debug(message: String) {
        logMessage(message, WHITE_ANSI)
    }
    fun warn(message: String) {
        logMessage(message, YELLOW_ANSI)
    }
    fun error(message: String) {
        logMessage(message, RED_ANSI)
    }
    private fun logMessage(message: String, color: String) {
        println("$color$message$RESET_ANSI")
    }
}
