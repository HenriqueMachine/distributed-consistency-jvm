package workshop.saga.pix.domain

import workshop.saga.contracts.Money

/** Um Pix liquidado: [amount] creditado na chave [to], identificado no SPI por [endToEndId]. */
data class PixTransfer(val transferId: Long, val to: String, val amount: Money, val endToEndId: String)

/** Resultado da análise do destino de um Pix. */
sealed interface PixDecision {
    /** O destino pode receber: segue para o SPI. */
    data object Send : PixDecision

    /** O destino não pode receber, pelo motivo [reason]: o "não" explícito que dispara a compensação. */
    data class Reject(val reason: String) : PixDecision
}

/**
 * Regra do "outro banco": aceita qualquer chave, menos as de contas encerradas. Na
 * apresentação, `conta-encerrada` é o jeito de forçar a compensação (slide 25).
 */
object PixPolicy {
    private val CLOSED_KEYS = setOf("conta-encerrada")

    /** Decide se a chave [to] pode receber o Pix. */
    fun evaluate(to: String): PixDecision =
        if (to in CLOSED_KEYS) PixDecision.Reject("conta destino encerrada chave=$to") else PixDecision.Send
}
