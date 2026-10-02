package workshop.saga.account.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import workshop.saga.account.domain.Debit
import workshop.saga.account.domain.Refund
import workshop.saga.account.infra.persistence.DebitRepository
import workshop.saga.account.infra.persistence.RefundRepository

/** Extrato de uma transferência: débitos e estornos, como a cliente veria (slide 29). */
data class Statement(val transferId: Long, val debits: List<Debit>, val refunds: List<Refund>)

/** Monta o extrato de uma transferência a partir dos débitos e estornos gravados. */
@Service
class StatementService(
    private val debits: DebitRepository,
    private val refunds: RefundRepository,
) {
    /** Extrato da transferência [transferId]; listas vazias se ela nunca foi debitada. */
    @Transactional(readOnly = true)
    fun of(transferId: Long) =
        Statement(transferId, debits.findAllByTransferId(transferId), refunds.findAllByTransferId(transferId))
}
