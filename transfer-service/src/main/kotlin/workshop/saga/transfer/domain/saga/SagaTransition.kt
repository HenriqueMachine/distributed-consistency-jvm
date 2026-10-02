package workshop.saga.transfer.domain.saga

import workshop.saga.transfer.domain.TransferId
import java.util.UUID

/**
 * Um fato: a saga da transferência [transferId] foi de [from] para [to] (slide 30). Fatos
 * não se apagam: cada transição vira uma linha nova em `saga_transitions`, nunca um UPDATE.
 *
 * [eventId] é a mensagem que causou a transição (nulo quando foi o relógio ou a criação),
 * [cid] é o correlation id dessa mensagem (ou a raiz `TRF-1042`) e [appVersion] é o git sha
 * do código que decidiu.
 */
data class SagaTransition(
    val transferId: TransferId,
    val from: SagaState,
    val to: SagaState,
    val reason: String,
    val eventId: UUID?,
    val cid: String,
    val appVersion: String,
)
