package snippetsearcher.printscript.linting

import org.printscript.analyzer.Diagnostic

// Dónde empieza la violación, en base 1 como SourceError
data class LintViolation(
    val rule: String,
    val message: String,
    val line: Int,
    val column: Int,
    val severity: String,
)

fun Diagnostic.toLintViolation() = LintViolation(rule, message, range.start.line, range.start.column, severity.name)
