package workshop.saga.transfer.domain.saga

import workshop.saga.transfer.domain.TransferId
import java.time.Instant

/**
 * Estado do processo de uma transferência. Separado de [workshop.saga.transfer.domain.Transfer]
 * de propósito: a transferência é o que o cliente pediu; a saga é o andamento do pedido.
 *
 * [deadlineAt] é até quando esperamos a resposta do passo atual; nulo quando não há prazo.
 * [attempts] conta quantas vezes o comando do passo atual já foi enviado.
 */
data class Saga(
    val transferId: TransferId,
    val state: SagaState,
    val deadlineAt: Instant? = null,
    val attempts: Int = 0,
) {
    /** O prazo do passo atual venceu em [now]? */
    fun isOverdue(now: Instant): Boolean = deadlineAt != null && !now.isBefore(deadlineAt)

    companion object {
        /** Uma saga nova, em CREATED, ainda sem prazo. */
        fun start(transferId: TransferId): Saga = Saga(transferId, SagaState.CREATED)
    }
}
