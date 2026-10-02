package workshop.saga.pix.infra.spi

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.stereotype.Component
import workshop.saga.pix.infra.messaging.PixCommandListener

/**
 * Circuito aberto = pausa o consumidor (slide 41). Enquanto o SPI está fora, não adianta
 * buscar mais `SendPix`: as mensagens ficam no tópico (o lag cresce no Kafka UI) em vez de
 * falhar uma a uma. Em HALF_OPEN o consumidor volta, para a chamada de teste passar.
 */
@Component
class SpiCircuitBreakerListener(
    circuitBreakers: CircuitBreakerRegistry,
    private val listeners: KafkaListenerEndpointRegistry,
) {
    init {
        circuitBreakers.circuitBreaker(SpiGateway.CIRCUIT).eventPublisher.onStateTransition { event ->
            val transition = event.stateTransition
            val container = listeners.getListenerContainer(PixCommandListener.LISTENER_ID)
            if (transition.toState in PAUSED_STATES) {
                log.warn("circuito {} {} → {}: pausando o consumidor {}", event.circuitBreakerName, transition.fromState, transition.toState, PixCommandListener.LISTENER_ID)
                container?.pause()
            } else {
                log.info("circuito {} {} → {}: retomando o consumidor {}", event.circuitBreakerName, transition.fromState, transition.toState, PixCommandListener.LISTENER_ID)
                container?.resume()
            }
        }
    }

    private companion object {
        /** Estados em que nenhuma chamada passa: não adianta consumir. */
        val PAUSED_STATES = setOf(CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN)
        val log = LoggerFactory.getLogger(SpiCircuitBreakerListener::class.java)
    }
}
