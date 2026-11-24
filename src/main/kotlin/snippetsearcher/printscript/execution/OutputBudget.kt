package snippetsearcher.printscript.execution

class OutputBudget(
    private val max: Long,
) {
    private var used = 0L

    fun spend(text: String) {
        used += text.length
        if (used > max) throw OutputTooLargeException(max)
    }
}
