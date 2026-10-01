package workshop.saga.account.infra.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import workshop.saga.account.application.ParticipantService
import workshop.saga.account.application.PixKeyAlreadyTakenException
import workshop.saga.account.domain.Account
import workshop.saga.account.domain.NewParticipant
import workshop.saga.contracts.Money
import java.math.BigDecimal
import java.net.URI

/** Corpo de `POST /participants`. Ex.: `{"name": "Maria Souza", "pixKey": "maria", "balance": 1000.00}`. */
data class RegisterParticipantRequest(val name: String, val pixKey: String, val balance: BigDecimal)

/** Um participante e o saldo atual da conta. */
data class ParticipantResponse(val pixKey: String, val name: String, val balance: BigDecimal) {
    companion object {
        /** Converte do domínio para a resposta HTTP. */
        fun from(account: Account) = ParticipantResponse(account.pixKey, account.name, account.balance.toDecimal())
    }
}

/** Porta HTTP do cadastro de participantes da apresentação. */
@RestController
@RequestMapping("/participants")
class ParticipantController(private val participants: ParticipantService) {

    /** Cadastra um participante; 409 se a chave Pix já existir. */
    @PostMapping
    fun register(@RequestBody request: RegisterParticipantRequest): ResponseEntity<ParticipantResponse> {
        val participant = NewParticipant(request.name, request.pixKey, Money.of(request.balance))
        val created = ParticipantResponse.from(participants.register(participant))
        return ResponseEntity.created(URI.create("/participants/${created.pixKey}")).body(created)
    }

    /** Todos os participantes com o saldo: bom para mostrar na tela antes e depois. */
    @GetMapping
    fun list(): List<ParticipantResponse> = participants.list().map(ParticipantResponse::from)

    /** Regra de validação violada no domínio vira 400, com a mensagem da regra. */
    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "requisição inválida")

    /** Chave Pix repetida vira 409. */
    @ExceptionHandler(PixKeyAlreadyTakenException::class)
    fun conflict(e: PixKeyAlreadyTakenException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message ?: "chave já cadastrada")
}
