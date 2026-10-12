package snippetsearcher.printscript.execution

import org.printscript.interpreter.io.PrintScriptIO

// Una instancia por ejecución: guarda estado del pedido y no se comparte.
class CollectingIo(
    inputs: List<String>,
) : PrintScriptIO {
    private val sent = inputs.size
    private val pending = ArrayDeque(inputs)
    private val printed = mutableListOf<String>()

    val outputs: List<String>
        get() = printed.toList()

    override fun print(text: String) {
        printed += text
    }

    // La librería imprime el prompt con print antes de llamar a read, así que acá se ignora
    override fun read(prompt: String): String = pending.removeFirstOrNull() ?: throw MissingInputException(sent)

    // Nunca se exponen las variables del servidor. La librería trata null como variable no definida.
    override fun env(name: String): String? = null
}
