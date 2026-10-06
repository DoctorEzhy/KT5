import java.time.LocalDateTime

enum class Priority(val level: Int, val title: String) {
    LOWEST(1, "Очень низкий"),
    LOW(2, "Низкий"),
    MEDIUM(3, "Средний"),
    HIGH(4, "Высокий"),
    CRITICAL(5, "Критический");

    companion object {
        fun fromLevel(level: Int): Priority? = values().find { it.level == level }
    }
}

enum class SortField { DATE, PRIORITY, TITLE }

data class Task(
    val id: Int,
    val title: String,
    val description: String,
    val priority: Priority,
    val isDone: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now().withNano(0)
)