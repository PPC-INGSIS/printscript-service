package snippetsearcher.printscript.execution

import org.printscript.common.Version
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Service
import snippetsearcher.printscript.common.Limits
import snippetsearcher.printscript.common.SourceError
import snippetsearcher.printscript.common.describe
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Service
class ExecutionService(
    private val limits: Limits,
    private val executor: AsyncTaskExecutor,
) {
    fun execute(
        code: String,
        version: Version,
        inputs: List<String>,
    ): ExecutionResponse {
        val io = CollectingIo(inputs, limits.maxOutputSize.toBytes())
        val error = runWithTimeout(code, version, io)
        return ExecutionResponse(io.outputs, error)
    }

    // El intérprete no chequea interrupciones: cancel(true) no lo frena y el thread puede seguir
    // corriendo después de responder. El timeout protege la latencia, no la memoria.
    private fun runWithTimeout(
        code: String,
        version: Version,
        io: CollectingIo,
    ): SourceError? {
        val future = executor.submit(Callable { runSnippet(code, version, io) })
        return try {
            future.get(limits.executionTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (expected: TimeoutException) {
            future.cancel(true)
            val message = "La ejecución superó el tiempo límite de ${limits.executionTimeout.describe()}"
            SourceError(message, line = null, column = null)
        } catch (exception: ExecutionException) {
            throw exception.cause ?: exception
        }
    }
}
