package snippetsearcher.printscript.execution

import snippetsearcher.printscript.common.SourceError

// El núcleo no sabe quién escucha estos eventos: el transporte los traduce a sus mensajes
sealed interface ExecutionEvent {
    data class Output(
        val text: String,
    ) : ExecutionEvent

    // Sin datos: el prompt ya salió como Output, porque la librería lo imprime antes de llamar a read
    data object InputRequested : ExecutionEvent

    data class Finished(
        val error: SourceError?,
    ) : ExecutionEvent
}
