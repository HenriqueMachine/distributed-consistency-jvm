package workshop.saga.transfer.infra.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.contracts.Money
import workshop.saga.transfer.domain.NewTransfer
import workshop.saga.transfer.domain.Transfer
import workshop.saga.transfer.domain.TransferId
import java.sql.ResultSet

/** Tabela `transfers`. O id vem de uma sequência que começa em 1042. */
@Repository
class TransferRepository(private val jdbc: JdbcClient) {

    /** Grava a transferência; o id vem da sequência. */
    fun insert(newTransfer: NewTransfer): Transfer {
        val id = jdbc.sql(
            """
            insert into transfers (from_key, to_key, amount_cents)
            values (:from, :to, :amountCents)
            returning id
            """,
        )
            .param("from", newTransfer.from)
            .param("to", newTransfer.to)
            .param("amountCents", newTransfer.amount.cents)
            .query(Long::class.java)
            .single()
        return Transfer(TransferId(id), newTransfer.from, newTransfer.to, newTransfer.amount)
    }

    /** A transferência, ou nulo se não existir. */
    fun findById(id: TransferId): Transfer? =
        jdbc.sql("select id, from_key, to_key, amount_cents from transfers where id = :id")
            .param("id", id.value)
            .query { rs, _ -> rs.toTransfer() }
            .optional()
            .orElse(null)

    private fun ResultSet.toTransfer() = Transfer(
        id = TransferId(getLong("id")),
        from = getString("from_key"),
        to = getString("to_key"),
        amount = Money(getLong("amount_cents")),
    )
}
