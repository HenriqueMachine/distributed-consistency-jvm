package workshop.saga.pix.infra.spi

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Interruptor da indisponibilidade simulada do SPI. Enquanto ligado, **toda** chamada ao
 * SPI falha, de qualquer transferência: é o cenário em que o circuit breaker faz sentido.
 */
@Component
class SpiOutage {
    private val clock = Clock.systemUTC()

    @Volatile
    private var downUntil: Instant? = null

    /** Derruba o SPI por [duration]. */
    fun start(duration: Duration): Instant = clock.instant().plus(duration).also { downUntil = it }

    /** Traz o SPI de volta na hora. */
    fun end() {
        downUntil = null
    }

    /** Até quando o SPI está fora; nulo se está no ar. */
    fun downUntil(): Instant? = downUntil?.takeIf { clock.instant().isBefore(it) }
}
