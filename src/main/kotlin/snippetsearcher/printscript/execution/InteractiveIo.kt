package snippetsearcher.printscript.execution

import org.printscript.interpreter.io.PrintScriptIO
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

// Una instancia por ejecución. print y read corren en el thread del snippet; provideInput llega desde otro.
class InteractiveIo(
    maxOutputSize: Long,
    private val inputTimeout: Duration,
    maxPendingInputs: Int,
    private val emit: (ExecutionEvent) -> Unit,
) : PrintScriptIO {
    private val budget = OutputBudget(maxOutputSize)
    private val inputs = ArrayBlockingQueue<String>(maxPendingInputs)

    override fun print(message: String) {
        // El intérprete no chequea interrupciones: un snippet que solo imprime se corta acá
        if (Thread.interrupted()) throw ExecutionCancelled()
        budget.spend(message)
        emit(ExecutionEvent.Output(message))
    }

    override fun read(prompt: String): String {
        emit(ExecutionEvent.InputRequested)
        val input =
            try {
                inputs.poll(inputTimeout.toNanos(), TimeUnit.NANOSECONDS)
            } catch (expected: InterruptedException) {
                throw ExecutionCancelled()
            }
        return input ?: throw InputTimeoutException(inputTimeout)
    }

    // Nunca se exponen las variables del servidor. La librería trata null como variable no definida.
    override fun env(name: String): String? = null

    // Con tope: un cliente que manda inputs sin que se los pidan no llena la memoria
    fun provideInput(value: String): Boolean = inputs.offer(value)
}
