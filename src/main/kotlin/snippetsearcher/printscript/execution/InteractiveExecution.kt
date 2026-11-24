package snippetsearcher.printscript.execution

import org.printscript.common.Version
import org.slf4j.LoggerFactory
import snippetsearcher.printscript.common.SourceError
import java.time.Duration

private val logger = LoggerFactory.getLogger(InteractiveExecution::class.java)
private val unexpectedError = SourceError("Error inesperado al ejecutar el snippet", line = null, column = null)

// Una por ejecución. Los eventos se emiten desde el thread del snippet, en orden.
class InteractiveExecution(
    code: String,
    version: Version,
    maxOutputSize: Long,
    inputTimeout: Duration,
    maxPendingInputs: Int,
    private val emit: (ExecutionEvent) -> Unit,
) {
    private val io = InteractiveIo(maxOutputSize, inputTimeout, maxPendingInputs, emit)

    // Virtual: esperar un input no ocupa un thread del pool de /execute
    private val thread = Thread.ofVirtual().unstarted { run(code, version) }

    fun start() = thread.start()

    fun provideInput(value: String): Boolean = io.provideInput(value)

    // La interrupción la convierte InteractiveIo en ExecutionCancelled, así que igual llega Finished
    fun cancel() = thread.interrupt()

    private fun run(
        code: String,
        version: Version,
    ) {
        val error =
            runCatching { runSnippet(code, version, io) }.getOrElse {
                // Se loguea antes de emitir: si lo que falló es el emit, el de Finished también puede fallar
                logger.error("Error inesperado en una ejecución interactiva", it)
                unexpectedError
            }
        emit(ExecutionEvent.Finished(error))
    }
}
