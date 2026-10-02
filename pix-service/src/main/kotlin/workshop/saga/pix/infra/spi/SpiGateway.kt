package workshop.saga.pix.infra.spi

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import workshop.saga.messaging.errors.DependencyUnavailableException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** O SPI não respondeu, ou o circuito está aberto e nem chegamos a chamar. */
class SpiUnavailableException(message: String, cause: Throwable? = null) : DependencyUnavailableException(message, cause)

/**
 * O SPI (Sistema de Pagamentos Instantâneos) simulado: o sistema de terceiros que liquida o
 * Pix. Devolve o `endToEndId`, a identidade da operação no ecossistema Pix.
 *
 * Toda chamada passa pelo circuit breaker `spi` (slide 39): passou de 50% de falhas, o
 * circuito abre e as chamadas nem saem; de tempos em tempos, uma passa para testar.
 */
@Component
class SpiGateway(
    private val outage: SpiOutage,
    circuitBreakers: CircuitBreakerRegistry,
) {
    private val circuitBreaker: CircuitBreaker = circuitBreakers.circuitBreaker(CIRCUIT)

    /** O que o SPI já liquidou: transferência → `endToEndId`. */
    private val settled = ConcurrentHashMap<Long, String>()

    /**
     * Liquida o Pix da transferência [transferId] e devolve o `endToEndId`.
     *
     * Como o PSP do slide 32, o SPI é idempotente pela chave: liquidar de novo a mesma
     * transferência devolve o mesmo `endToEndId`, sem um segundo Pix. É isso que torna
     * seguro chamá-lo dentro da transação do banco: se ela voltar, o retry recebe a mesma
     * resposta.
     */
    fun settle(transferId: Long): String =
        try {
            circuitBreaker.executeSupplier { callSpi(transferId) }
        } catch (e: CallNotPermittedException) {
            throw SpiUnavailableException("circuito $CIRCUIT aberto: SPI não foi chamado", e)
        }

    private fun callSpi(transferId: Long): String {
        if (outage.downUntil() != null) {
            log.warn("SPI indisponível")
            throw SpiUnavailableException("SPI indisponível")
        }
        return settled.computeIfAbsent(transferId) { "E$it${UUID.randomUUID().toString().take(6)}" }
            .also { log.info("SPI liquidou endToEndId={}", it) }
    }

    companion object {
        /** Nome do circuit breaker no `application.yml`. */
        const val CIRCUIT = "spi"
        private val log = LoggerFactory.getLogger(SpiGateway::class.java)
    }
}
