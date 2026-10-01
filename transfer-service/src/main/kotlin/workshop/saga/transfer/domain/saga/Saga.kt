package workshop.saga.transfer.domain.saga

import workshop.saga.transfer.domain.TransferId

/**
 * Estado do processo de uma transferência. Separado de [workshop.saga.transfer.domain.Transfer]
 * de propósito: a transferência é o que o cliente pediu; a saga é o andamento do pedido.
 */
data class Saga(
    val transferId: TransferId,
    val state: SagaState,
) {
    companion object {
        /** Uma saga nova, em CREATED, ainda sem prazo. */
        fun start(transferId: TransferId): Saga = Saga(transferId, SagaState.CREATED)
    }
}
