package workshop.saga.messaging.errors

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.util.backoff.FixedBackOff
import workshop.saga.contracts.InvalidPayloadException
import workshop.saga.contracts.Topics

/**
 * O que fazer quando um listener lança exceção (slide 41).
 *
 * - **Erro transitório** (banco fora, rede, lock): tenta de novo com backoff exponencial,
 *   1 s, 2 s, 4 s. Esgotou, a mensagem vai para a DLT.
 * - **Erro permanente** ([InvalidPayloadException]): retry não ajuda, vai direto para a DLT.
 * - **Dependência fora do ar** ([DependencyUnavailableException]): não é culpa da mensagem;
 *   tenta de novo a cada 2 s, sem limite, e nunca vai para a DLT (slide 42).
 *
 * Nos dois primeiros casos a mensagem sai do caminho e a partição volta a andar: as
 * mensagens das outras transferências não ficam presas atrás dela. O Spring Boot liga este
 * handler a todos os `@KafkaListener` do serviço.
 *
 * Cuidado com o retry bloqueante longo (slide 45): 1 + 2 + 4 = 7 s parado nesta mensagem,
 * bem abaixo do `max.poll.interval.ms` (5 min). E o prazo da saga para este passo precisa
 * ser maior que isso.
 */
@Configuration
class KafkaErrorHandlingConfig {

    /** O handler de erro de todos os listeners do serviço. */
    @Bean
    fun kafkaErrorHandler(kafka: KafkaTemplate<String, String>, meters: MeterRegistry): DefaultErrorHandler =
        DefaultErrorHandler(deadLetterRecoverer(kafka, meters), retryBackOff()).apply {
            addNotRetryableExceptions(InvalidPayloadException::class.java)
            setBackOffFunction { _, exception ->
                if (exception.causeOfType<DependencyUnavailableException>() != null) WAIT_FOR_DEPENDENCY else null
            }
        }

    private fun retryBackOff() = ExponentialBackOffWithMaxRetries(MAX_RETRIES).apply {
        initialInterval = 1_000
        multiplier = 2.0
    }

    /**
     * Publica na DLT (com headers explicando a falha), deixa um rastro legível no log e
     * conta a mensagem em `saga.dlt.messages`: toda DLT precisa de alerta (slide 41).
     * O `transferId` e o `cid` já estão no MDC: o interceptor os pôs antes do listener.
     */
    private fun deadLetterRecoverer(kafka: KafkaTemplate<String, String>, meters: MeterRegistry): ConsumerRecordRecoverer {
        val publisher = DeadLetterPublishingRecoverer(kafka) { record, _ ->
            TopicPartition(Topics.deadLetterOf(record.topic()), record.partition())
        }
        return ConsumerRecordRecoverer { record, exception ->
            val deadLetterTopic = Topics.deadLetterOf(record.topic())
            publisher.accept(record, exception)
            deadLetterCounter(meters, deadLetterTopic).increment()
            val invalidPayload = exception.causeOfType<InvalidPayloadException>()
            log.error(
                "falha {} → {}: {}",
                if (invalidPayload != null) "permanente, sem retry" else "tentativa=$MAX_RETRIES",
                deadLetterTopic,
                (invalidPayload ?: exception.rootCause()).message,
            )
        }
    }

    private fun deadLetterCounter(meters: MeterRegistry, topic: String): Counter =
        Counter.builder("saga.dlt.messages")
            .description("Mensagens que esgotaram as tentativas e foram para a DLT")
            .tag("topic", topic)
            .register(meters)

    private inline fun <reified T : Throwable> Throwable.causeOfType(): T? =
        generateSequence(this) { it.cause }.filterIsInstance<T>().firstOrNull()

    private fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()

    private companion object {
        /** Retries depois da primeira tentativa: 1 s, 2 s, 4 s. O log conta "tentativa=3" como no slide 47. */
        const val MAX_RETRIES = 3

        /** Dependência fora do ar: tenta a cada 2 s até ela voltar. */
        val WAIT_FOR_DEPENDENCY = FixedBackOff(2_000, FixedBackOff.UNLIMITED_ATTEMPTS)

        val log = LoggerFactory.getLogger(KafkaErrorHandlingConfig::class.java)
    }
}
