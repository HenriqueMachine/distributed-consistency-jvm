package workshop.saga.messaging.errors

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
 * O que fazer quando um listener lança exceção (slide 34).
 *
 * - **Erro transitório** (banco fora, rede, lock): tenta de novo com backoff exponencial,
 *   1 s, 2 s, 4 s. Esgotou, a mensagem vai para a DLT.
 * - **Erro permanente** ([InvalidPayloadException]): retry não ajuda, vai direto para a DLT.
 * - **Dependência fora do ar** ([DependencyUnavailableException]): não é culpa da mensagem;
 *   tenta de novo a cada 2 s, sem limite, e nunca vai para a DLT (slide 35).
 *
 * Nos dois primeiros casos a mensagem sai do caminho e a partição volta a andar: as
 * mensagens das outras transferências não ficam presas atrás dela. O Spring Boot liga este
 * handler a todos os `@KafkaListener` do serviço.
 *
 * Cuidado com o retry bloqueante longo (slide 38): 1 + 2 + 4 = 7 s parado nesta mensagem,
 * bem abaixo do `max.poll.interval.ms` (5 min). E o prazo da saga para este passo precisa
 * ser maior que isso.
 */
@Configuration
class KafkaErrorHandlingConfig {

    /** O handler de erro de todos os listeners do serviço. */
    @Bean
    fun kafkaErrorHandler(kafka: KafkaTemplate<String, String>): DefaultErrorHandler =
        DefaultErrorHandler(deadLetterRecoverer(kafka), retryBackOff()).apply {
            addNotRetryableExceptions(InvalidPayloadException::class.java)
            setBackOffFunction { _, exception ->
                if (exception.causeOfType<DependencyUnavailableException>() != null) WAIT_FOR_DEPENDENCY else null
            }
        }

    private fun retryBackOff() = ExponentialBackOffWithMaxRetries(MAX_RETRIES).apply {
        initialInterval = 1_000
        multiplier = 2.0
    }

    /** Publica na DLT (com headers explicando a falha) e deixa um rastro legível no log. */
    private fun deadLetterRecoverer(kafka: KafkaTemplate<String, String>): ConsumerRecordRecoverer {
        val publisher = DeadLetterPublishingRecoverer(kafka) { record, _ ->
            TopicPartition(Topics.deadLetterOf(record.topic()), record.partition())
        }
        return ConsumerRecordRecoverer { record, exception ->
            publisher.accept(record, exception)
            val invalidPayload = exception.causeOfType<InvalidPayloadException>()
            // ⚠ QUEBRA passo-7: aqui a transferência aparece só como a chave crua. As linhas que
            // o próprio Spring Kafka loga entre os retries não dizem de qual transferência são.
            log.error(
                "{} falha {} → {}: {}",
                record.key(),
                if (invalidPayload != null) "permanente, sem retry" else "tentativa=$MAX_RETRIES",
                Topics.deadLetterOf(record.topic()),
                (invalidPayload ?: exception.rootCause()).message,
            )
        }
    }

    private inline fun <reified T : Throwable> Throwable.causeOfType(): T? =
        generateSequence(this) { it.cause }.filterIsInstance<T>().firstOrNull()

    private fun Throwable.rootCause(): Throwable = generateSequence(this) { it.cause }.last()

    private companion object {
        /** Retries depois da primeira tentativa: 1 s, 2 s, 4 s. O log conta "tentativa=3" como no slide 40. */
        const val MAX_RETRIES = 3

        /** Dependência fora do ar: tenta a cada 2 s até ela voltar. */
        val WAIT_FOR_DEPENDENCY = FixedBackOff(2_000, FixedBackOff.UNLIMITED_ATTEMPTS)

        val log = LoggerFactory.getLogger(KafkaErrorHandlingConfig::class.java)
    }
}
