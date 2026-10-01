package workshop.saga.mortician.domain

import java.util.UUID

/** Uma mensagem recém-chegada de uma DLT, antes de ganhar id e status. */
data class IncomingDeadLetter(
    val deadLetterTopic: String,
    val partition: Int,
    val offset: Long,
    val originalTopic: String,
    val key: String,
    val messageId: UUID?,
    val messageType: String?,
    val payload: String,
    val error: String,
    val cid: String?,
) {
    /** A chave da mensagem é o transferId; nulo se a chave não for um número (ex.: lixo injetado). */
    val transferId: Long? get() = key.toLongOrNull()
}
