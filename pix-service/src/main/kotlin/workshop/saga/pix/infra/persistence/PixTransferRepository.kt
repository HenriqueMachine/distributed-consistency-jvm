package workshop.saga.pix.infra.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.contracts.Money
import workshop.saga.pix.domain.PixTransfer

/** Tabela `pix_transfers`: um registro por Pix liquidado (o crédito no destino). */
@Repository
class PixTransferRepository(private val jdbc: JdbcClient) {

    /** Grava o Pix liquidado. */
    fun insert(pix: PixTransfer) {
        jdbc.sql(
            """
            insert into pix_transfers (transfer_id, to_key, amount_cents, end_to_end_id)
            values (:transferId, :to, :amount, :endToEndId)
            """,
        )
            .param("transferId", pix.transferId)
            .param("to", pix.to)
            .param("amount", pix.amount.cents)
            .param("endToEndId", pix.endToEndId)
            .update()
    }

    /** Todos os Pix da transferência. */
    fun findAllByTransferId(transferId: Long): List<PixTransfer> =
        jdbc.sql("select transfer_id, to_key, amount_cents, end_to_end_id from pix_transfers where transfer_id = :transferId order by id")
            .param("transferId", transferId)
            .query { rs, _ ->
                PixTransfer(rs.getLong("transfer_id"), rs.getString("to_key"), Money(rs.getLong("amount_cents")), rs.getString("end_to_end_id"))
            }
            .list()
}
