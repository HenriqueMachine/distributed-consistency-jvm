package workshop.saga.transfer.infra.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import workshop.saga.contracts.Money
import workshop.saga.transfer.application.TransferService
import workshop.saga.transfer.application.TransferSummary
import workshop.saga.transfer.domain.NewTransfer
import workshop.saga.transfer.domain.TransferId
import java.math.BigDecimal
import java.net.URI

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

/** Porta HTTP do transfer-service: criar transferências e consultar o estado da saga. */
@RestController
@RequestMapping("/transfers")
class TransferController(private val transferService: TransferService) {

    /** Cria a transferência e devolve 201 com o estado inicial da saga. */
    @PostMapping
    fun create(@RequestBody request: CreateTransferRequest): ResponseEntity<TransferResponse> {
        val newTransfer = NewTransfer(request.from, request.to, Money.of(request.amount))
        val created = TransferResponse.from(transferService.create(newTransfer))
        return ResponseEntity.created(URI.create("/transfers/${created.id}")).body(created)
    }

    /** A transferência e o estado atual da saga; 404 se não existir. */
    @GetMapping("/{id}")
    fun find(@PathVariable id: Long): ResponseEntity<TransferResponse> =
        transferService.find(TransferId(id))
            ?.let { ResponseEntity.ok(TransferResponse.from(it)) }
            ?: ResponseEntity.notFound().build()

    /** Regra de validação violada no domínio vira 400, com a mensagem da regra. */
    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "requisição inválida")
}
