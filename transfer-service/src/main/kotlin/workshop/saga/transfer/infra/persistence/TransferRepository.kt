package workshop.saga.transfer.infra.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.contracts.Money
import workshop.saga.contracts.Simulation
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
            insert into transfers (from_key, to_key, amount_cents, simulate)
            values (:from, :to, :amountCents, :simulate)
            returning id
            """,
        )
            .param("from", newTransfer.from)
            .param("to", newTransfer.to)
            .param("amountCents", newTransfer.amount.cents)
            .param("simulate", newTransfer.simulation?.name)
            .query(Long::class.java)
            .single()
        return Transfer(TransferId(id), newTransfer.from, newTransfer.to, newTransfer.amount, newTransfer.simulation)
    }

    /** A transferência, ou nulo se não existir. */
    fun findById(id: TransferId): Transfer? =
        jdbc.sql("select id, from_key, to_key, amount_cents, simulate from transfers where id = :id")
            .param("id", id.value)
            .query { rs, _ -> rs.toTransfer() }
            .optional()
            .orElse(null)

    private fun ResultSet.toTransfer() = Transfer(
        id = TransferId(getLong("id")),
        from = getString("from_key"),
        to = getString("to_key"),
        amount = Money(getLong("amount_cents")),
        simulation = getString("simulate")?.let(Simulation::valueOf),
    )
}
