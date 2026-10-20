package snippetsearcher.printscript.execution

// El código llega en el body, como en /validate: leerlo del store es responsabilidad de Snippets.
// inputs se consumen en orden, uno por cada readInput.
data class ExecutionRequest(
    val code: String,
    val version: String,
    val inputs: List<String>,
)
