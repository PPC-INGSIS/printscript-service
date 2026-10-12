package snippetsearcher.printscript.source

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// La cátedra todavía no dio el Snippet Store, así que no hay cliente real.
// Este bean existe solo para que el contexto levante: cualquier lectura falla con 500.
// Cuando haya cliente, se reemplaza por la implementación que le pide el código al store.
@Configuration
class SnippetReaderConfiguration {
    @Bean
    fun snippetReader() = SnippetReader { throw UnsupportedOperationException("Snippet Store no configurado") }
}
