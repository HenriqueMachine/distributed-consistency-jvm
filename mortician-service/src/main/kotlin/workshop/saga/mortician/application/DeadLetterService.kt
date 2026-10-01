package workshop.saga.mortician.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import workshop.saga.messaging.outbox.OutboxRecord
import workshop.saga.messaging.outbox.OutboxRepository
import workshop.saga.mortician.domain.AlreadyRepublishedException
import workshop.saga.mortician.domain.DeadLetter
import workshop.saga.mortician.domain.DeadLetterStatus
import workshop.saga.mortician.domain.IncomingDeadLetter
import workshop.saga.mortician.infra.persistence.DeadLetterRepository
import java.time.Clock
import java.util.UUID

/** A mensagem pedida não existe. */
class DeadLetterNotFoundException(id: Long) : RuntimeException("dead letter $id não existe")

/** Casos de uso do Mortician: enterrar, listar, investigar e republicar (slide 37). */
@Service
class DeadLetterService(
    private val deadLetters: DeadLetterRepository,
    private val outbox: OutboxRepository,
    private val clock: Clock,
) {
    /** Guarda uma mensagem que chegou de uma DLT. Uma entrega repetida da mesma mensagem é ignorada. */
    @Transactional
    fun bury(incoming: IncomingDeadLetter) {
        val id = deadLetters.insertIfAbsent(incoming) ?: return
        log.warn(
            "{} dead letter {} guardada: {} {} → {}: {}",
            incoming.transferId ?: "-", id, incoming.messageType, incoming.originalTopic, incoming.deadLetterTopic, incoming.error,
        )
    }

    /** Mensagens mortas, das mais novas para as mais antigas, com filtros opcionais. */
    @Transactional(readOnly = true)
    fun list(status: DeadLetterStatus?, transferId: Long?): List<DeadLetter> = deadLetters.find(status, transferId)

    /** Uma mensagem morta, com payload e erro, para investigar. */
    @Transactional(readOnly = true)
    fun get(id: Long): DeadLetter = deadLetters.findById(id) ?: throw DeadLetterNotFoundException(id)

    /**
     * Republica a mensagem no tópico original, **pela outbox** (senão é escrita dupla de novo)
     * e **sem** o header `simulate`: é a "correção do bug" antes do resgate. A mensagem ganha
     * um `messageId` novo: a idempotência de negócio do destino cobre a repetição, e a mesma
     * mensagem pode morrer e ser resgatada de novo sem colidir na outbox.
     */
    @Transactional
    fun republish(id: Long, by: String, reason: String): DeadLetter {
        val rescued = get(id).republish(by, reason, clock.instant())
        if (!deadLetters.markRepublished(rescued)) throw AlreadyRepublishedException(id)
        outbox.save(
            OutboxRecord(
                messageId = UUID.randomUUID(),
                topic = rescued.originalTopic,
                key = rescued.key,
                type = rescued.messageType ?: "",
                payload = rescued.payload,
                simulation = null,
            ),
        )
        log.info("{} dead letter {} republicada em {} por {}: {}", rescued.transferId ?: "-", id, rescued.originalTopic, by, reason)
        return rescued
    }

    private companion object {
        val log = LoggerFactory.getLogger(DeadLetterService::class.java)
    }
}
