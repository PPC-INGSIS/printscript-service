package snippetsearcher.printscript.source

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

// La cátedra todavía no dio el Snippet Store: mientras tanto los snippets se leen de una carpeta local.
// Cuando haya cliente, se reemplaza solo este bean por la implementación que le pide el código al store.
@Configuration
class SnippetReaderConfiguration {
    @Bean
    fun snippetReader(
        @Value("\${snippet-store.local-dir}") directory: Path,
    ): SnippetReader = LocalSnippetReader(directory)
}
