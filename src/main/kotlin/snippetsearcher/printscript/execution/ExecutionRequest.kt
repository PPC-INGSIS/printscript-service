package snippetsearcher.printscript.execution

// inputs se consumen en orden, uno por cada readInput
data class ExecutionRequest(
    val path: String,
    val version: String,
    val inputs: List<String>,
)
