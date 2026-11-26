package snippetsearcher.printscript.common

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("snippetsearcher.printscript.common.DeepNesting")

// Sin número: el límite depende del tamaño del stack de la JVM
private val tooDeepError =
    SourceError("El código tiene demasiados niveles de anidamiento", line = null, column = null)

fun <T> handlingDeepNesting(
    onTooDeep: (SourceError) -> T,
    block: () -> T,
): T =
    try {
        block()
    } catch (overflow: StackOverflowError) {
        // Sin stack trace: lo causa el código del usuario, no el servidor
        logger.warn("Un snippet desbordó el stack: {}", overflow.toString())
        onTooDeep(tooDeepError)
    }
