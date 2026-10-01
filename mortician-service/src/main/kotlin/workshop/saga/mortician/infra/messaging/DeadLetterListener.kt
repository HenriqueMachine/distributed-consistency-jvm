package workshop.saga.mortician.infra.messaging

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Component
import workshop.saga.contracts.MessageHeaders
import workshop.saga.mortician.application.DeadLetterService
import workshop.saga.mortician.domain.IncomingDeadLetter
import java.util.UUID

/**
 * Consome **todas** as DLTs (`*.DLT`) e entrega cada mensagem ao [DeadLetterService]. Os
 * headers que o `DeadLetterPublishingRecoverer` acrescentou contam de onde ela veio e por
 * que morreu.
 */
@Component
class DeadLetterListener(private val deadLetters: DeadLetterService) {

    /** Uma mensagem morta, de qualquer DLT. */
    @KafkaListener(id = "mortician", idIsGroup = false, topicPattern = ".*\\.DLT")
    fun bury(record: ConsumerRecord<String, String>) {
        deadLetters.bury(
            IncomingDeadLetter(
                deadLetterTopic = record.topic(),
                partition = record.partition(),
                offset = record.offset(),
                originalTopic = record.header(KafkaHeaders.DLT_ORIGINAL_TOPIC) ?: record.topic().removeSuffix(".DLT"),
                key = record.key() ?: "",
                messageId = record.header(MessageHeaders.MESSAGE_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                messageType = record.header(MessageHeaders.MESSAGE_TYPE),
                payload = record.value() ?: "",
                error = listOfNotNull(
                    record.header(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)?.substringAfterLast('.'),
                    record.header(KafkaHeaders.DLT_EXCEPTION_MESSAGE)?.withoutListenerPrefix(),
                ).joinToString(": ").ifEmpty { "erro desconhecido" },
            ),
        )
    }

    private fun ConsumerRecord<String, String>.header(name: String): String? =
        headers().lastHeader(name)?.value()?.toString(Charsets.UTF_8)

    /** O Spring Kafka prefixa "Listener method '…' threw exception; " ao erro real; na triagem, só o erro importa. */
    private fun String.withoutListenerPrefix(): String = substringAfter(LISTENER_PREFIX_END, this)

    private companion object {
        const val LISTENER_PREFIX_END = "threw exception; "
    }
}
