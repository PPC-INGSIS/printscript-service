package snippetsearcher.printscript.validation

import java.io.FilterInputStream
import java.io.InputStream

class CodeTooLargeException : RuntimeException()

class LimitedInputStream(
    source: InputStream,
    private val maxBytes: Long,
) : FilterInputStream(source) {
    private var read = 0L

    override fun read(): Int = super.read().also { if (it != -1) count(1) }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = super.read(buffer, offset, length).also { if (it > 0) count(it) }

    private fun count(bytes: Int) {
        read += bytes
        if (read > maxBytes) throw CodeTooLargeException()
    }
}
