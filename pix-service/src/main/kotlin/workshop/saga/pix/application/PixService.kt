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
import workshop.saga.messaging.inbox.Inbox
import workshop.saga.pix.domain.PixDecision
import workshop.saga.pix.domain.PixPolicy
import workshop.saga.pix.domain.PixTransfer
import workshop.saga.pix.infra.persistence.PixTransferRepository
import workshop.saga.pix.infra.spi.SpiGateway

/**
 * Caso de uso do pix-service: enviar o Pix ao SPI e creditar o destino, uma única vez.
 * Mesma idempotência em duas camadas do account-service.
 */
@Service
class PixService(
    private val pixTransfers: PixTransferRepository,
    private val spi: SpiGateway,
    private val publisher: MessagePublisher,
    private val inbox: Inbox,
    private val failureSimulator: FailureSimulator,
) {
    /** Liquida o Pix uma única vez, ou recusa se o destino não puder receber. */
    @Transactional
    fun send(command: SendPix, request: Envelope) {
        if (!inbox.firstDelivery(request)) return
        failureSimulator.beforeSend(request)

        val existing = pixTransfers.findByTransferId(command.transferId)
        if (existing != null) {
            log.info("{} Pix já liquidado endToEndId={} → devolvendo resultado anterior", command.transferId, existing.endToEndId)
            publisher.publish(Envelope.of(PixSettled(command.transferId, existing.endToEndId), request.simulation))
            return
        }

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
