package snippetsearcher.printscript

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PrintScriptApplication

// Una sola copia de los argumentos al arrancar: no hay costo real que evitar
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<PrintScriptApplication>(*args)
}
