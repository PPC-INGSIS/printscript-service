package snippetsearcher.printscript.execution

import snippetsearcher.printscript.common.SourceError

// Si hubo error, outputs tiene lo impreso hasta ese momento
data class ExecutionResponse(
    val outputs: List<String>,
    val error: SourceError?,
)
