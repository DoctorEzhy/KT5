import java.io.File
import java.time.format.DateTimeFormatter

private class InputClosedException : RuntimeException()

class MenuController(private val repository: TaskRepository) {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val defaultFile = "tasks.csv"


    fun run() {
        try {
            while (true) {
                printMenu()
                val choice = readLineOrExit("Ваш выбор: ")
                if (choice == "0") {
                    println("До свидания!")
                    return
                }
                try {
                    handle(choice)
                } catch (e: InputClosedException) {
                    throw e
                } catch (e: Exception) {
                    println("Ошибка: ${e.message}")
                    ErrorLogger.log("Ошибка в пункте меню «$choice»", e)
                }
            }
        } catch (e: InputClosedException) {
            println("\nВвод закрыт, завершаю работу.")
        }
    }

    private fun printMenu() {
        println(
            """
            |
            |===== МЕНЕДЖЕР ЗАДАЧ =====
            |1 — Создать задачу
            |2 — Показать все задачи
            |3 — Найти задачу
            |4 — Редактировать задачу
            |5 — Удалить задачу
            |6 — Сортировка
            |7 — Сохранить в файл
            |8 — Загрузить из файла
            |9 — Отметить несколько задач выполненными
            |0 — Выход
            """.trimMargin()
        )
    }

    private fun handle(choice: String) {
        when (choice) {
            "1" -> createTask()
            "2" -> printTable(repository.getAll())
            "3" -> searchTasks()
            "4" -> editTask()
            "5" -> deleteTask()
            "6" -> sortTasks()
            "7" -> saveTasks()
            "8" -> loadTasks()
            "9" -> markSeveralDone()
            else -> println("Неизвестный пункт меню. Введите число от 0 до 9.")
        }
    }


    private fun createTask() {
        val title = readRequired("Название: ")
        val description = readLineOrExit("Описание: ")
        val priority = readPriority("Приоритет (1-5, где 5 — самый важный): ")

        val task = repository.add(title, description, priority)
        println("Задача создана, ID = ${task.id}")
    }

    private fun searchTasks() {
        println("Оставь поле пустым, чтобы не использовать это условие.")
        val title = readLineOrExit("Часть названия: ").ifEmpty { null }
        val priority = readOptionalPriority("Приоритет (1-5): ")
        val isDone = readOptionalBool("Выполнена? (y/n): ")

        printTable(repository.search(title, priority, isDone))
    }

    private fun editTask() {
        val id = readInt("ID задачи для редактирования: ")
        val task = repository.findById(id)
        if (task == null) {
            println("Задача с ID $id не найдена.")
            return
        }

        println("Текущая задача:")
        printTable(listOf(task))
        println("Описание: ${task.description}")
        println("Оставь поле пустым, чтобы сохранить старое значение.")

        val newTitle = readLineOrExit("Название [${task.title}]: ").ifEmpty { null }
        val newDescription = readLineOrExit("Описание [${task.description}]: ").ifEmpty { null }
        val newPriority = readOptionalPriority("Приоритет [${task.priority.level}]: ")
        val newDone = readOptionalBool("Выполнена? (y/n) [${if (task.isDone) "y" else "n"}]: ")

        repository.update(id, newTitle, newDescription, newPriority, newDone)
        println("Задача обновлена.")
    }

    private fun deleteTask() {
        val id = readInt("ID задачи для удаления: ")
        val task = repository.findById(id)
        if (task == null) {
            println("Задача с ID $id не найдена.")
            return
        }

        printTable(listOf(task))
        if (readYesNo("Вы уверены, что хотите удалить? (y/n): ")) {
            repository.delete(id)
            println("Задача удалена.")
        } else {
            println("Удаление отменено.")
        }
    }

    private fun sortTasks() {
        println("Сортировать по: 1 — дате создания, 2 — приоритету, 3 — названию")
        val field = when (readLineOrExit("Выбор: ")) {
            "1" -> SortField.DATE
            "2" -> SortField.PRIORITY
            "3" -> SortField.TITLE
            else -> {
                println("Неверный выбор.")
                return
            }
        }

        println("Порядок: 1 — по возрастанию, 2 — по убыванию")
        val ascending = when (readLineOrExit("Выбор: ")) {
            "1" -> true
            "2" -> false
            else -> {
                println("Неверный выбор.")
                return
            }
        }

        printTable(repository.sorted(field, ascending))
    }

    private fun saveTasks() {
        val path = readLineOrExit("Имя файла [$defaultFile]: ").ifEmpty { defaultFile }
        repository.saveToFile(path)
        println("Сохранено задач: ${repository.getAll().size}")
        println("Файл: ${File(path).absolutePath}")
    }

    private fun loadTasks() {
        val path = readLineOrExit("Имя файла [$defaultFile]: ").ifEmpty { defaultFile }

        if (repository.getAll().isNotEmpty() &&
            !readYesNo("Текущие задачи будут заменены. Продолжить? (y/n): ")
        ) {
            println("Загрузка отменена.")
            return
        }

        val result = repository.loadFromFile(path)
        println("Загружено задач: ${result.loaded}")
        if (result.skipped > 0) {
            println("Пропущено повреждённых строк: ${result.skipped} (подробности в errors.log)")
        }
    }

    private fun markSeveralDone() {
        val text = readRequired("ID задач через пробел или запятую: ")
        val ids = text.split(Regex("[,\\s]+")).mapNotNull { it.toIntOrNull() }
        if (ids.isEmpty()) {
            println("Не найдено ни одного числа.")
            return
        }

        val missing = repository.markDone(ids)
        println("Отмечено выполненными: ${ids.distinct().size - missing.size}")
        if (missing.isNotEmpty()) {
            println("Не найдены ID: ${missing.joinToString()}")
        }
    }


    private fun printTable(tasks: List<Task>) {
        if (tasks.isEmpty()) {
            println("Задач не найдено.")
            return
        }

        println("ID".padEnd(5) + "Title".padEnd(24) + "Priority".padEnd(10) + "Done".padEnd(6) + "CreatedAt")
        println("-".repeat(62))
        for (task in tasks) {
            println(
                task.id.toString().padEnd(5) +
                        shorten(task.title, 22).padEnd(24) +
                        task.priority.level.toString().padEnd(10) +
                        (if (task.isDone) "Yes" else "No").padEnd(6) +
                        task.createdAt.format(dateFormat)
            )
        }
    }

    private fun shorten(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1) + "…"


    private fun readLineOrExit(prompt: String): String {
        print(prompt)
        return readLine()?.trim() ?: throw InputClosedException()
    }

    private fun readRequired(prompt: String): String {
        while (true) {
            val text = readLineOrExit(prompt)
            if (text.isNotEmpty()) return text
            println("Поле не может быть пустым.")
        }
    }

    private fun readInt(prompt: String): Int {
        while (true) {
            val number = readLineOrExit(prompt).toIntOrNull()
            if (number != null) return number
            println("Введите целое число.")
        }
    }

    private fun readOptionalPriority(prompt: String): Priority? {
        while (true) {
            val text = readLineOrExit(prompt)
            if (text.isEmpty()) return null
            val priority = text.toIntOrNull()?.let { Priority.fromLevel(it) }
            if (priority != null) return priority
            println("Приоритет — число от 1 до 5.")
        }
    }

    private fun readPriority(prompt: String): Priority {
        while (true) {
            readOptionalPriority(prompt)?.let { return it }
            println("Приоритет обязателен.")
        }
    }

    private fun readOptionalBool(prompt: String): Boolean? {
        while (true) {
            when (readLineOrExit(prompt).lowercase()) {
                "" -> return null
                "y", "yes", "д", "да" -> return true
                "n", "no", "н", "нет" -> return false
                else -> println("Введите y или n (или оставьте пустым).")
            }
        }
    }

    private fun readYesNo(prompt: String): Boolean {
        while (true) {
            readOptionalBool(prompt)?.let { return it }
            println("Нужно ответить y или n.")
        }
    }
}