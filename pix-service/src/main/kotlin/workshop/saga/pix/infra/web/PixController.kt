package workshop.saga.pix.infra.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import workshop.saga.pix.application.PixService
import java.math.BigDecimal

/** Um crédito feito pelo Pix. */
data class PixCreditResponse(val to: String, val amount: BigDecimal, val endToEndId: String)

/** Porta HTTP de leitura do pix-service. */
@RestController
class PixController(private val pixService: PixService) {

    /** `GET /pix?transferId=1042`: quantas vezes o destino foi creditado. */
    @GetMapping("/pix")
    fun credits(@RequestParam transferId: Long): List<PixCreditResponse> =
        pixService.creditsOf(transferId).map { PixCreditResponse(it.to, it.amount.toDecimal(), it.endToEndId) }
}
