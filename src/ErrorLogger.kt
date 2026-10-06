import java.io.File
import java.time.LocalDateTime

object ErrorLogger {
    private const val LOG_FILE = "errors.log"

    fun log(message: String, error: Throwable? = null) {
        try {
            val details = if (error != null) " | ${error::class.simpleName}: ${error.message}" else ""
            File(LOG_FILE).appendText("${LocalDateTime.now().withNano(0)} $message$details\n")
        } catch (e: Exception) {
            println("(не удалось записать лог: ${e.message})")
        }
    }
}