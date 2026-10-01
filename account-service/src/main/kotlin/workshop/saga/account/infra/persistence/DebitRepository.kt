package workshop.saga.account.infra.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.account.domain.Debit
import workshop.saga.account.domain.DebitId
import workshop.saga.contracts.Money
import java.sql.ResultSet

/** Tabela `debits`: uma linha por débito. */
@Repository
class DebitRepository(private val jdbc: JdbcClient) {

    /** Grava um débito e devolve o seu id. */
    fun insert(transferId: Long, from: String, amount: Money): Debit {
        val id = jdbc.sql(
            "insert into debits (transfer_id, from_key, amount_cents) values (:transferId, :from, :amount) returning id",
        )
            .param("transferId", transferId)
            .param("from", from)
            .param("amount", amount.cents)
            .query(Long::class.java)
            .single()
        return Debit(DebitId(id), transferId, from, amount)
    }

    /** O primeiro débito da transferência, se houver. */
    fun findFirstByTransferId(transferId: Long): Debit? = findAllByTransferId(transferId).firstOrNull()

    /** Todos os débitos da transferência (extrato). */
    fun findAllByTransferId(transferId: Long): List<Debit> =
        jdbc.sql("select id, transfer_id, from_key, amount_cents from debits where transfer_id = :transferId order by id")
            .param("transferId", transferId)
            .query { rs, _ -> rs.toDebit() }
            .list()

    private fun ResultSet.toDebit() =
        Debit(DebitId(getLong("id")), getLong("transfer_id"), getString("from_key"), Money(getLong("amount_cents")))
}
