package snippetsearcher.printscript.execution

import org.printscript.interpreter.io.PrintScriptIO
import java.util.Collections

// Una instancia por ejecución: guarda estado del pedido y no se comparte.
// Si la ejecución vence, outputs se lee desde otro thread mientras un print en curso puede terminar de agregar.
class CollectingIo(
    inputs: List<String>,
    maxOutputSize: Long,
) : PrintScriptIO {
    private val sent = inputs.size
    private val pending = ArrayDeque(inputs)
    private val printed = Collections.synchronizedList(mutableListOf<String>())
    private val budget = OutputBudget(maxOutputSize)

    val outputs: List<String>
        get() = synchronized(printed) { printed.toList() }

    override fun print(text: String) {
        if (Thread.interrupted()) throw ExecutionCancelled()
        budget.spend(text)
        printed += text
    }

    // La librería imprime el prompt con print antes de llamar a read, así que acá se ignora
    override fun read(prompt: String): String {
        if (Thread.interrupted()) throw ExecutionCancelled()
        return pending.removeFirstOrNull() ?: throw MissingInputException(sent)
    }

    // Nunca se exponen las variables del servidor. La librería trata null como variable no definida.
    override fun env(name: String): String? = null
}
