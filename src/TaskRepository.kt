import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class LoadResult(val loaded: Int, val skipped: Int)

class TaskRepository {

    private val tasks = mutableListOf<Task>()
    private var nextId = 1


    fun add(title: String, description: String, priority: Priority): Task {
        val task = Task(id = nextId++, title = title, description = description, priority = priority)
        tasks.add(task)
        return task
    }

    fun getAll(): List<Task> = tasks.toList()

    fun findById(id: Int): Task? = tasks.find { it.id == id }


    fun delete(id: Int): Boolean = tasks.removeIf { it.id == id }


    fun update(
        id: Int,
        title: String? = null,
        description: String? = null,
        priority: Priority? = null,
        isDone: Boolean? = null
    ): Boolean {
        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return false

        val old = tasks[index]
        tasks[index] = old.copy(
            title = title ?: old.title,
            description = description ?: old.description,
            priority = priority ?: old.priority,
            isDone = isDone ?: old.isDone
        )
        return true
    }

    fun markDone(ids: List<Int>): List<Int> =
        ids.distinct().filter { id -> !update(id, isDone = true) }


    fun search(title: String?, priority: Priority?, isDone: Boolean?): List<Task> =
        tasks.filter { task ->
            (title == null || task.title.contains(title, ignoreCase = true)) &&
                    (priority == null || task.priority == priority) &&
                    (isDone == null || task.isDone == isDone)
        }


    fun sorted(field: SortField, ascending: Boolean): List<Task> {
        val comparator: Comparator<Task> = when (field) {
            SortField.DATE -> compareBy<Task> { it.createdAt }
            SortField.PRIORITY -> compareBy<Task> { it.priority.level }
            SortField.TITLE -> compareBy<Task> { it.title.lowercase() }
        }
        return tasks.sortedWith(if (ascending) comparator else comparator.reversed())
    }


    fun saveToFile(path: String) {
        val lines = mutableListOf("id,title,description,priority,isDone,createdAt")
        for (task in tasks) {
            lines.add(
                listOf(
                    task.id,
                    csvQuote(task.title),
                    csvQuote(task.description),
                    task.priority.level,
                    task.isDone,
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(task.createdAt)
                ).joinToString(",")
            )
        }
        File(path).writeText(lines.joinToString("\n") + "\n")
    }


    fun loadFromFile(path: String): LoadResult {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("Файл «$path» не найден")
        }

        val loaded = mutableListOf<Task>()
        var skipped = 0

        for ((index, line) in file.readLines().withIndex()) {
            if (line.isBlank() || (index == 0 && line.startsWith("id,"))) continue
            try {
                val task = parseTask(line)
                if (loaded.any { it.id == task.id }) {
                    throw IllegalArgumentException("повторяющийся ID ${task.id}")
                }
                loaded.add(task)
            } catch (e: Exception) {
                skipped++
                ErrorLogger.log("Файл $path, строка ${index + 1} пропущена", e)
            }
        }

        if (loaded.isEmpty() && skipped > 0) {
            throw IllegalArgumentException(
                "В файле нет корректных задач (пропущено строк: $skipped). Текущие данные не изменены"
            )
        }

        tasks.clear()
        tasks.addAll(loaded)
        nextId = (tasks.maxOfOrNull { it.id } ?: 0) + 1
        return LoadResult(loaded.size, skipped)
    }


    private fun csvQuote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun parseTask(line: String): Task {
        val fields = parseCsvLine(line)
        require(fields.size == 6) { "ожидалось 6 полей, найдено ${fields.size}" }

        val id = fields[0].trim().toInt()
        require(id > 0) { "ID должен быть больше 0" }
        require(fields[1].isNotBlank()) { "пустое название" }

        val priority = Priority.fromLevel(fields[3].trim().toInt())
            ?: throw IllegalArgumentException("приоритет должен быть от 1 до 5")

        val isDone = when (fields[4].trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("isDone должно быть true или false")
        }

        val createdAt = LocalDateTime.parse(fields[5].trim())
        return Task(id, fields[1], fields[2], priority, isDone, createdAt)
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}