package workshop.saga.transfer.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import workshop.saga.contracts.Cid
import workshop.saga.messaging.observability.SagaContext
import workshop.saga.transfer.domain.NewTransfer
import workshop.saga.transfer.domain.Transfer
import workshop.saga.transfer.domain.TransferId
import workshop.saga.transfer.domain.saga.SagaState
import workshop.saga.transfer.domain.saga.SagaTransition
import workshop.saga.transfer.infra.persistence.SagaRepository
import workshop.saga.transfer.infra.persistence.SagaTransitionRepository
import workshop.saga.transfer.infra.persistence.TransferRepository

/** Visão de leitura de uma transferência: o que foi pedido e em que estado a saga está. */
data class TransferSummary(val transfer: Transfer, val state: SagaState)

/** Caso de uso de entrada: criar e consultar transferências. */
@Service
class TransferService(
    private val transfers: TransferRepository,
    private val sagas: SagaRepository,
    private val transitions: SagaTransitionRepository,
    private val orchestrator: SagaOrchestrator,
    private val failureSimulator: FailureSimulator,
) {
    /**
     * Grava a transferência e dispara a saga.
     *
     * Passo 3 (slide 30): o `DebitAccount` é gravado na outbox, na mesma transação da
     * transferência. Se algo falhar antes do commit, transferência e mensagem somem juntas.
     */
    @Transactional
    fun create(newTransfer: NewTransfer): TransferSummary {
        val transfer = transfers.insert(newTransfer)
        return SagaContext.with(transfer.id, Cid.root(transfer.id.value)) {
            log.info("transferência criada {} → {} valor={}", transfer.from, transfer.to, transfer.amount)
            val saga = orchestrator.start(transfer)
            failureSimulator.afterCommandsSent(transfer)
            TransferSummary(transfer, saga.state)
        }
    } // o commit grava transferência, saga e outbox de uma vez; o relay publica depois

    /** A transferência e o estado da saga, ou nulo se ela não existir. */
    @Transactional(readOnly = true)
    fun find(id: TransferId): TransferSummary? {
        val transfer = transfers.findById(id) ?: return null
        val saga = sagas.findByTransferId(id) ?: return null
        return TransferSummary(transfer, saga.state)
    }

    /** Todas as transições da saga, da primeira à última: "por que a 1042 foi estornada?". */
    @Transactional(readOnly = true)
    fun transitionsOf(id: TransferId): List<SagaTransition> = transitions.findByTransferId(id)

    private companion object {
        val log = LoggerFactory.getLogger(TransferService::class.java)
    }
}
