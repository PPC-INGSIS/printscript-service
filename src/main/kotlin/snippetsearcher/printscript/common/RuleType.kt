package snippetsearcher.printscript.common

import java.math.BigInteger

sealed interface Parsed<out T> {
    data class Valid<T>(
        val value: T,
    ) : Parsed<T>

    data class Problem(
        val message: String,
    ) : Parsed<Nothing>
}

sealed interface RuleType<T : Any> {
    fun parse(value: Any?): Parsed<T>

    // JSON Schema del valor que acepta la regla
    fun schema(default: Any?): Map<String, Any?>

    data class Choice<T : Any>(
        val options: Map<String, T>,
    ) : RuleType<T> {
        override fun parse(value: Any?): Parsed<T> =
            options[value]?.let { Parsed.Valid(it) }
                ?: Parsed.Problem("tiene que ser ${options.keys.joinToString(" o ") { "'$it'" }}")

        override fun schema(default: Any?) = mapOf("type" to "string", "enum" to options.keys, "default" to default)
    }

    // of es el constructor de la librería, que devuelve null fuera de rango
    class Integer<T : Any>(
        val range: IntRange,
        private val of: (Int) -> T?,
    ) : RuleType<T> {
        // Jackson deja en Long o BigInteger los enteros que no entran en un Int: son enteros, pero fuera de rango
        override fun parse(value: Any?): Parsed<T> =
            when (value) {
                is Int -> of(value)?.let { Parsed.Valid(it) } ?: outOfRange(value)
                is Long, is BigInteger -> outOfRange(value)
                else -> Parsed.Problem("espera un número entero")
            }

        override fun schema(default: Any?) =
            mapOf("type" to "integer", "minimum" to range.first, "maximum" to range.last, "default" to default)

        private fun outOfRange(value: Any) =
            Parsed.Problem("acepta valores entre ${range.first} y ${range.last}, se recibió $value")
    }

    // Solo true activa la regla: false equivale a no mandarla
    data object Flag : RuleType<Boolean> {
        override fun parse(value: Any?): Parsed<Boolean> =
            if (value is Boolean) Parsed.Valid(value) else Parsed.Problem("solo acepta true")

        override fun schema(default: Any?) = mapOf("type" to "boolean", "const" to true)
    }
}
