package workshop.saga.pix.infra.web

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import workshop.saga.pix.infra.spi.SpiGateway
import workshop.saga.pix.infra.spi.SpiOutage
import java.time.Duration
import java.time.Instant

/** Estado do SPI simulado e do circuito que o protege. */
data class SpiStatus(val downUntil: Instant?, val circuit: String)

/** Porta HTTP para derrubar o SPI na demonstração do circuit breaker (passo 6). */
@RestController
@RequestMapping("/spi")
class SpiController(
    private val outage: SpiOutage,
    private val circuitBreakers: CircuitBreakerRegistry,
) {
    /** `POST /spi/outage?seconds=15`: o SPI fica fora do ar para todos por esse tempo. */
    @PostMapping("/outage")
    fun startOutage(@RequestParam(defaultValue = "15") seconds: Long): SpiStatus {
        outage.start(Duration.ofSeconds(seconds))
        log.warn("SPI fora do ar por {}s", seconds)
        return status()
    }

    /** `DELETE /spi/outage`: o SPI volta na hora. */
    @DeleteMapping("/outage")
    fun endOutage(): SpiStatus {
        outage.end()
        log.info("SPI de volta")
        return status()
    }

    /** Se o SPI está fora e em que estado está o circuito. */
    @GetMapping
    fun status() = SpiStatus(outage.downUntil(), circuitBreakers.circuitBreaker(SpiGateway.CIRCUIT).state.name)

    private companion object {
        val log = LoggerFactory.getLogger(SpiController::class.java)
    }
}
