package snippetsearcher.printscript.execution

import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Programa el plazo del `start` de cada conexión interactiva.
 *
 * Aparte del handler para que el handler siga siendo solo el protocolo, y para que el thread tenga
 * dueño: se apaga con el contexto de Spring.
 */
@Component
class StartDeadlines {
    // Un solo thread alcanza: lo único que corre acá es rechazar conexiones, y eso no bloquea
    private val scheduler =
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "start-deadlines").apply { isDaemon = true }
        }

    fun schedule(
        timeout: Duration,
        action: () -> Unit,
    ): ScheduledFuture<*> = scheduler.schedule(Runnable { action() }, timeout.toMillis(), TimeUnit.MILLISECONDS)

    @PreDestroy
    fun shutdown() {
        scheduler.shutdownNow()
    }
}
