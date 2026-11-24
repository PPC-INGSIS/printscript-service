package snippetsearcher.printscript.execution

// runSnippet atrapa solo este tipo: un IO nuevo corta la ejecución extendiéndola
open class ExecutionAborted(
    message: String,
) : RuntimeException(message)
