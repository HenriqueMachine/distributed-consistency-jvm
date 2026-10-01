package workshop.saga.contracts

import java.util.concurrent.ThreadLocalRandom

/**
 * Correlation id em árvore (slide 41): quem produz a próxima mensagem acrescenta um
 * segmento ao id, e o id conta o caminho.
 *
 * ```
 * TRF-1042
 * ├─ TRF-1042.DEB-a1          DebitAccount #1
 * ├─ TRF-1042.DEB-b7          DebitAccount #2 (reenvio)
 * └─ TRF-1042.PIX-c3          SendPix
 *    └─ TRF-1042.PIX-c3.SPI-3f   chamada ao SPI
 * ```
 *
 * Um `grep TRF-1042` traz a saga inteira, e a tentativa 1 do débito se separa da tentativa 2.
 */
@JvmInline
value class Cid(val value: String) {

    /** Um filho deste id: `TRF-1042` + `PIX` → `TRF-1042.PIX-c3`. */
    fun child(segment: String): Cid = Cid("$value.$segment-${shortId()}")

    override fun toString(): String = value

    companion object {
        /** A raiz da árvore de uma transferência: `TRF-1042`. */
        fun root(transferId: Long) = Cid("TRF-$transferId")

        /** Dois caracteres hexadecimais: o bastante para separar os irmãos de um mesmo nó. */
        private fun shortId(): String = "%02x".format(ThreadLocalRandom.current().nextInt(256))
    }
}
