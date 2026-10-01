package workshop.saga.mortician.infra.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import workshop.saga.mortician.application.DeadLetterNotFoundException
import workshop.saga.mortician.application.DeadLetterService
import workshop.saga.mortician.domain.AlreadyRepublishedException
import workshop.saga.mortician.domain.DeadLetter
import workshop.saga.mortician.domain.DeadLetterStatus
import java.time.Instant
import java.util.UUID

/** Corpo de `POST /dead-letters/{id}/republish`: quem resgata e por quê. */
data class RepublishRequest(val reason: String, val requestedBy: String = "apresentador")

/** Uma mensagem morta, como o Mortician a mostra. */
data class DeadLetterResponse(
    val id: Long,
    val transferId: Long?,
    val cid: String?,
    val status: String,
    val originalTopic: String,
    val deadLetterTopic: String,
    val messageType: String?,
    val messageId: UUID?,
    val error: String,
    val payload: String,
    val receivedAt: Instant,
    val republishedBy: String?,
    val republishReason: String?,
    val republishedAt: Instant?,
) {
    companion object {
        /** Converte a mensagem morta do domínio para a resposta HTTP. */
        fun from(d: DeadLetter) = DeadLetterResponse(
            d.id, d.transferId, d.cid, d.status.name, d.originalTopic, d.deadLetterTopic, d.messageType, d.messageId,
            d.error, d.payload, d.receivedAt, d.rescue?.by, d.rescue?.reason, d.rescue?.at,
        )
    }
}

/** Porta HTTP do Mortician: listar, investigar e republicar mensagens mortas (slide 37). */
@RestController
@RequestMapping("/dead-letters")
class DeadLetterController(private val deadLetters: DeadLetterService) {

    /** `GET /dead-letters?status=NEW&transferId=1042`. */
    @GetMapping
    fun list(
        @RequestParam(required = false) status: DeadLetterStatus?,
        @RequestParam(required = false) transferId: Long?,
    ): List<DeadLetterResponse> = deadLetters.list(status, transferId).map(DeadLetterResponse::from)

    /** Uma mensagem morta, com payload e erro. */
    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): DeadLetterResponse = DeadLetterResponse.from(deadLetters.get(id))

    /** Republica no tópico original, pela outbox. 409 se ela já foi republicada. */
    @PostMapping("/{id}/republish")
    fun republish(@PathVariable id: Long, @RequestBody request: RepublishRequest): DeadLetterResponse =
        DeadLetterResponse.from(deadLetters.republish(id, request.requestedBy, request.reason))

    /** Mensagem inexistente vira 404. */
    @ExceptionHandler(DeadLetterNotFoundException::class)
    fun notFound(e: DeadLetterNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message ?: "não encontrada")

    /** Resgate repetido vira 409. */
    @ExceptionHandler(AlreadyRepublishedException::class)
    fun conflict(e: AlreadyRepublishedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message ?: "conflito")

    /** Resgate sem motivo ou sem dono vira 400. */
    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "requisição inválida")
}
