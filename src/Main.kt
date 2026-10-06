//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
// Здесь только запуск: никакой логики и никаких вызовов репозитория
fun main() {
    val repository = TaskRepository()
    MenuController(repository).run()
}