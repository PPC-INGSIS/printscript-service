package snippetsearcher.printscript.common

import org.printscript.common.PrintScriptError

// Dónde empieza el error. Línea y columna tal como las da la librería: base 1.
// Son null solo cuando la librería no informa posición (faltan inputs al ejecutar).
data class SourceError(
    val message: String,
    val line: Int?,
    val column: Int?,
)

fun PrintScriptError.toSourceError() = SourceError(message, range.start.line, range.start.column)
