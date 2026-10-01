package workshop.saga.account.infra.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import workshop.saga.account.application.StatementService
import java.math.BigDecimal

/** Linha do extrato. */
data class StatementEntry(val debitId: String, val amount: BigDecimal)

/** `GET /debits?transferId=1042`: quantas vezes a conta foi debitada e estornada. */
data class StatementResponse(val transferId: Long, val debits: List<StatementEntry>, val refunds: List<StatementEntry>)

/** Porta HTTP de leitura do account-service: o extrato de uma transferência. */
@RestController
class StatementController(private val statements: StatementService) {

    /** Débitos e estornos da transferência [transferId]. */
    @GetMapping("/debits")
    fun statement(@RequestParam transferId: Long): StatementResponse {
        val statement = statements.of(transferId)
        return StatementResponse(
            transferId = transferId,
            debits = statement.debits.map { StatementEntry(it.id.toString(), it.amount.toDecimal()) },
            refunds = statement.refunds.map { StatementEntry(it.debitId.toString(), it.amount.toDecimal()) },
        )
    }
}
