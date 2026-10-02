package workshop.saga.messaging.outbox

import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import workshop.saga.contracts.MessageHeaders
import workshop.saga.contracts.Simulation
import workshop.saga.messaging.observability.SagaContext

/**
 * O "alguém envia" da caixa de saída (slide 10): lê as linhas pendentes da outbox, publica
 * no Kafka e marca `published_at`.
 *
 * Se o relay cair depois de publicar e antes de marcar, na volta ele publica de novo.
 * A outbox garante que nada se perde; **não** garante que nada se repete.
 */
@Component
class OutboxRelay(
    private val outbox: OutboxRepository,
    private val kafka: KafkaTemplate<String, String>,
) {
    /**
     * Uma rodada: publica um lote e marca tudo de uma vez. Os envios são síncronos e
     * acontecem com as linhas travadas; se um deles falhar, a transação volta e o lote
     * inteiro, inclusive o que já tinha saído, é publicado de novo na próxima rodada.
     * É at-least-once de propósito: quem recebe é idempotente (passo 4).
     */
    @Scheduled(fixedDelayString = "\${outbox.relay.interval:200ms}")
    @Transactional
    fun relayPending() {
        val pending = outbox.lockPending(BATCH_SIZE)
        pending.forEach { record ->
            SagaContext.with(record.key, record.cid) {
                repeat(timesToPublish(record)) { send(record) }
            }
        }
        outbox.markPublished(pending.map { it.messageId })
    }

    private fun send(record: OutboxRecord) {
        val producerRecord = ProducerRecord<String, String>(record.topic, record.key, record.payload).apply {
            headers().add(MessageHeaders.MESSAGE_ID, record.messageId.toString().toByteArray())
            headers().add(MessageHeaders.MESSAGE_TYPE, record.type.toByteArray())
            record.cid?.let { headers().add(MessageHeaders.CID, it.value.toByteArray()) }
            record.simulation?.let { headers().add(MessageHeaders.SIMULATE, it.name.toByteArray()) }
        }
        // .get(): só marcamos published_at depois do ack do broker.
        kafka.send(producerRecord).get()
        log.debug("relay publicou {} messageId={} tópico={}", record.type, record.messageId, record.topic)
    }

    /** `DUPLICATE` simula a queda entre publicar e marcar: a mesma linha sai duas vezes. */
    private fun timesToPublish(record: OutboxRecord): Int =
        if (record.simulation == Simulation.DUPLICATE) {
            log.warn(
                "relay simulate=DUPLICATE: {} messageId={} publicado 2× (queda antes de marcar published_at)",
                record.type, record.messageId,
            )
            2
        } else {
            1
        }

    private companion object {
        /** Lote por rodada: depois de uma queda, o relay não inunda o tópico (slide 45). */
        const val BATCH_SIZE = 100
        val log = LoggerFactory.getLogger(OutboxRelay::class.java)
    }
}
