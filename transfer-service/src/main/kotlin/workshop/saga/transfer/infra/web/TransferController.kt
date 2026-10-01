package workshop.saga.transfer.infra.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import workshop.saga.contracts.Money
import workshop.saga.contracts.Simulation
import workshop.saga.transfer.application.SimulatedFailureException
import workshop.saga.transfer.application.TransferService
import workshop.saga.transfer.application.TransferSummary
import workshop.saga.transfer.domain.NewTransfer
import workshop.saga.transfer.domain.TransferId
import workshop.saga.transfer.domain.saga.SagaTransition
import java.math.BigDecimal
import java.net.URI
import java.util.UUID

/** Corpo de `POST /transfers`. Ex.: `{"from": "ana", "to": "henrique", "amount": 150.00}`. */
data class CreateTransferRequest(val from: String, val to: String, val amount: BigDecimal)

/** Resposta de leitura de uma transferência, com o estado atual da saga. */
data class TransferResponse(val id: Long, val from: String, val to: String, val amount: BigDecimal, val state: String) {
    companion object {
        /** Converte do domínio para a resposta HTTP. */
        fun from(summary: TransferSummary) = with(summary.transfer) {
            TransferResponse(id.value, from, to, amount.toDecimal(), summary.state.name)
        }
    }
}

/** Uma linha do histórico da saga (`saga_transitions`). */
data class TransitionResponse(
    val from: String,
    val to: String,
    val reason: String,
    val eventId: UUID?,
    val cid: String,
    val appVersion: String,
) {
    companion object {
        /** Converte do domínio para a resposta HTTP. */
        fun from(t: SagaTransition) = TransitionResponse(t.from.name, t.to.name, t.reason, t.eventId, t.cid, t.appVersion)
    }
}

/** Porta HTTP do transfer-service: criar transferências e consultar a saga. */
@RestController
@RequestMapping("/transfers")
class TransferController(private val transferService: TransferService) {

    /** Cria a transferência. `X-Simulate` escolhe uma falha para ela (ver [Simulation]). */
    @PostMapping
    fun create(
        @RequestBody request: CreateTransferRequest,
        @RequestHeader("X-Simulate", required = false) simulate: String?,
    ): ResponseEntity<TransferResponse> {
        val newTransfer = NewTransfer(request.from, request.to, Money.of(request.amount), Simulation.parse(simulate))
        val created = TransferResponse.from(transferService.create(newTransfer))
        return ResponseEntity.created(URI.create("/transfers/${created.id}")).body(created)
    }

    /** A transferência e o estado atual da saga; 404 se não existir (ex.: `CRASH_AFTER_SEND`). */
    @GetMapping("/{id}")
    fun find(@PathVariable id: Long): ResponseEntity<TransferResponse> =
        transferService.find(TransferId(id))
            ?.let { ResponseEntity.ok(TransferResponse.from(it)) }
            ?: ResponseEntity.notFound().build()

    /** O histórico da saga (slide 23): cada transição com motivo, evento, cid e versão do código. */
    @GetMapping("/{id}/transitions")
    fun transitions(@PathVariable id: Long): List<TransitionResponse> =
        transferService.transitionsOf(TransferId(id)).map(TransitionResponse::from)

    /** Regra de validação violada no domínio vira 400, com a mensagem da regra. */
    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "requisição inválida")

    /** Falha provocada por `X-Simulate`: 500, com o `transferId` para quem quiser investigar. */
    @ExceptionHandler(SimulatedFailureException::class)
    fun simulatedFailure(e: SimulatedFailureException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.message ?: "falha simulada")
            .apply { setProperty("transferId", e.transferId.value) }
}
