package workshop.saga.mortician.domain

import java.time.Instant
import java.util.UUID

/** A mensagem morta já foi republicada: o histórico do resgate não se sobrescreve. */
class AlreadyRepublishedException(id: Long) : RuntimeException("dead letter $id já foi republicada")

/** Em que ponto da triagem uma mensagem morta está. */
enum class DeadLetterStatus {
    /** Chegou da DLT e ninguém mexeu ainda. */
    NEW,

    /** Alguém republicou no tópico original; quem, quando e por quê ficam registrados. */
    REPUBLISHED,
}

/**
 * Uma mensagem que morreu numa DLT, com tudo o que é preciso para investigar e resgatar:
 * de onde veio, o payload original, o erro e a transferência.
 */
data class DeadLetter(
    val id: Long,
    val deadLetterTopic: String,
    val originalTopic: String,
    val key: String,
    val messageId: UUID?,
    val messageType: String?,
    val payload: String,
    val error: String,
    val transferId: Long?,
    val cid: String?,
    val status: DeadLetterStatus,
    val receivedAt: Instant,
    val rescue: Rescue? = null,
) {
    /** O registro de um resgate. */
    data class Rescue(val by: String, val reason: String, val at: Instant)

    /**
     * Marca o resgate. Uma mensagem só é republicada uma vez: o histórico de quem resgatou
     * não pode ser sobrescrito.
     */
    fun republish(by: String, reason: String, at: Instant): DeadLetter {
        if (status != DeadLetterStatus.NEW) throw AlreadyRepublishedException(id)
        require(reason.isNotBlank()) { "reason é obrigatório: todo resgate precisa de um porquê" }
        require(by.isNotBlank()) { "requestedBy é obrigatório: todo resgate precisa de um dono" }
        return copy(status = DeadLetterStatus.REPUBLISHED, rescue = Rescue(by.trim(), reason.trim(), at))
    }
}
