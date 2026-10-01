package workshop.saga.pix.infra.spi

import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * O SPI (Sistema de Pagamentos Instantâneos) simulado: o sistema de terceiros que liquida o
 * Pix. Devolve o `endToEndId`, a identidade da operação no ecossistema Pix.
 */
@Component
class SpiGateway {
    /** O que o SPI já liquidou: transferência → `endToEndId`. */
    private val settled = ConcurrentHashMap<Long, String>()

    /**
     * Liquida o Pix da transferência [transferId] e devolve o `endToEndId`.
     *
     * Como o PSP do slide 28, o SPI é idempotente pela chave: liquidar de novo a mesma
     * transferência devolve o mesmo `endToEndId`, sem um segundo Pix. É isso que torna
     * seguro chamá-lo dentro da transação do banco: se ela voltar, o retry recebe a mesma
     * resposta.
     */
    fun settle(transferId: Long): String =
        settled.computeIfAbsent(transferId) { "E$it${UUID.randomUUID().toString().take(6)}" }
}
