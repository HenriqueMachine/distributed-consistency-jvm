package workshop.saga.pix.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import workshop.saga.contracts.Envelope
import workshop.saga.contracts.Money
import workshop.saga.contracts.PixRejected
import workshop.saga.contracts.PixSettled
import workshop.saga.contracts.SendPix
import workshop.saga.messaging.MessagePublisher
import workshop.saga.pix.domain.PixDecision
import workshop.saga.pix.domain.PixPolicy
import workshop.saga.pix.domain.PixTransfer
import workshop.saga.pix.infra.persistence.PixTransferRepository
import workshop.saga.pix.infra.spi.SpiGateway

/** Caso de uso do pix-service: enviar o Pix ao SPI e creditar o destino. */
@Service
class PixService(
    private val pixTransfers: PixTransferRepository,
    private val spi: SpiGateway,
    private val publisher: MessagePublisher,
) {
    /**
     * Liquida o Pix, ou recusa se o destino não puder receber.
     *
     * ⚠ QUEBRA passo-3: assim como no account-service, uma entrega repetida do mesmo
     * `SendPix` liquida (e credita o destino) de novo.
     */
    @Transactional
    fun send(command: SendPix, request: Envelope) {
        val amount = Money(command.amountInCents)
        val reply = when (val decision = PixPolicy.evaluate(command.to)) {
            PixDecision.Send -> {
                val pix = PixTransfer(command.transferId, command.to, amount, spi.settle(command.transferId))
                pixTransfers.insert(pix)
                log.info("{} Pix liquidado {} para {} endToEndId={} → PixSettled", pix.transferId, amount, pix.to, pix.endToEndId)
                PixSettled(command.transferId, pix.endToEndId)
            }
            is PixDecision.Reject -> {
                log.info("{} {} → PixRejected", command.transferId, decision.reason)
                PixRejected(command.transferId, decision.reason)
            }
        }
        publisher.publish(Envelope.of(reply, request.simulation))
    }

    /** Os créditos feitos para a transferência [transferId]. */
    @Transactional(readOnly = true)
    fun creditsOf(transferId: Long): List<PixTransfer> = pixTransfers.findAllByTransferId(transferId)

    private companion object {
        val log = LoggerFactory.getLogger(PixService::class.java)
    }
}
