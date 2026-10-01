package workshop.saga.account.infra.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.account.domain.Debit
import workshop.saga.account.domain.DebitId
import workshop.saga.account.domain.Refund
import workshop.saga.contracts.Money

/** Tabela `refunds`: estornos, sempre ligados a um débito. */
@Repository
class RefundRepository(private val jdbc: JdbcClient) {

    /** Grava o estorno de [debit]. */
    fun insert(debit: Debit) {
        jdbc.sql("insert into refunds (debit_id, transfer_id, amount_cents) values (:debitId, :transferId, :amount)")
            .param("debitId", debit.id.value)
            .param("transferId", debit.transferId)
            .param("amount", debit.amount.cents)
            .update()
    }

    /** Todos os estornos da transferência (extrato). */
    fun findAllByTransferId(transferId: Long): List<Refund> =
        jdbc.sql("select debit_id, transfer_id, amount_cents from refunds where transfer_id = :transferId order by id")
            .param("transferId", transferId)
            .query { rs, _ -> Refund(DebitId(rs.getLong("debit_id")), rs.getLong("transfer_id"), Money(rs.getLong("amount_cents"))) }
            .list()
}
