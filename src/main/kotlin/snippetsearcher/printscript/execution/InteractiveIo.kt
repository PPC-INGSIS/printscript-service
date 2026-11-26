package snippetsearcher.printscript.execution

import org.printscript.interpreter.io.PrintScriptIO
import snippetsearcher.printscript.common.InteractiveLimits
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

// Una instancia por ejecución. print y read corren en el thread del snippet; provideInput llega desde otro.
class InteractiveIo(
    maxOutputSize: Long,
    private val limits: InteractiveLimits,
    private val emit: (ExecutionEvent) -> Unit,
) : PrintScriptIO {
    private val budget = OutputBudget(maxOutputSize)
    private val inputs = ArrayBlockingQueue<String>(limits.maxPendingInputs)

    // Lo escribe y lo lee solo el thread del snippet
    private var deadline = 0L

    // Se llama al arrancar la ejecución, no al crearla: los inputs pueden encolarse antes
    fun startClock() {
        deadline = System.nanoTime() + limits.maxDuration.toNanos()
    }

    override fun print(message: String) {
        // El intérprete no chequea interrupciones: un snippet que solo imprime se corta acá
        if (Thread.interrupted()) throw ExecutionCancelled()
        if (remainingNanos() <= 0) throw MaxDurationExceededException(limits.maxDuration)
        budget.spend(message)
        emit(ExecutionEvent.Output(message))
    }

    override fun read(prompt: String): String {
        emit(ExecutionEvent.InputRequested)
        val remaining = remainingNanos()
        val inputTimeout = limits.inputTimeout.toNanos()
        val input =
            try {
                inputs.poll(minOf(inputTimeout, remaining), TimeUnit.NANOSECONDS)
            } catch (expected: InterruptedException) {
                throw ExecutionCancelled()
            }
        if (input != null) return input
        throw if (inputTimeout <= remaining) {
            InputTimeoutException(limits.inputTimeout)
        } else {
            MaxDurationExceededException(limits.maxDuration)
        }
    }

    // Nunca se exponen las variables del servidor. La librería trata null como variable no definida.
    override fun env(name: String): String? = null

    // Con tope: un cliente que manda inputs sin que se los pidan no llena la memoria
    fun provideInput(value: String): Boolean = inputs.offer(value)

    private fun remainingNanos() = deadline - System.nanoTime()
}
