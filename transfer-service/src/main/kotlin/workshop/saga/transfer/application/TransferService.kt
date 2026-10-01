package workshop.saga.transfer.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import workshop.saga.transfer.domain.NewTransfer
import workshop.saga.transfer.domain.Transfer
import workshop.saga.transfer.domain.TransferId
import workshop.saga.transfer.domain.saga.Saga
import workshop.saga.transfer.domain.saga.SagaState
import workshop.saga.transfer.infra.persistence.SagaRepository
import workshop.saga.transfer.infra.persistence.TransferRepository

/** Visão de leitura de uma transferência: o que foi pedido e em que estado a saga está. */
data class TransferSummary(val transfer: Transfer, val state: SagaState)

/** Caso de uso de entrada: criar e consultar transferências. */
@Service
class TransferService(
    private val transfers: TransferRepository,
    private val sagas: SagaRepository,
) {
    /** Grava a transferência e a saga no mesmo commit. No passo 1 a história para aqui. */
    @Transactional
    fun create(newTransfer: NewTransfer): TransferSummary {
        val transfer = transfers.insert(newTransfer)
        val saga = Saga.start(transfer.id)
        sagas.insert(saga)
        log.info(
            "transferência {} criada {} → {} valor={} → {}",
            transfer.id, transfer.from, transfer.to, transfer.amount, saga.state,
        )
        return TransferSummary(transfer, saga.state)
    }

    /** A transferência e o estado da saga, ou nulo se ela não existir. */
    @Transactional(readOnly = true)
    fun find(id: TransferId): TransferSummary? {
        val transfer = transfers.findById(id) ?: return null
        val saga = sagas.findByTransferId(id) ?: return null
        return TransferSummary(transfer, saga.state)
    }

    private companion object {
        val log = LoggerFactory.getLogger(TransferService::class.java)
    }
}
