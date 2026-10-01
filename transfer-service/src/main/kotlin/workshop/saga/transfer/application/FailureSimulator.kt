package workshop.saga.transfer.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import workshop.saga.contracts.Simulation
import workshop.saga.transfer.domain.Transfer
import workshop.saga.transfer.domain.TransferId

/** Falha provocada de propósito pela apresentação. Nunca acontece sem `X-Simulate`. */
class SimulatedFailureException(val transferId: TransferId, message: String) : RuntimeException(message)

/**
 * Único lugar do transfer-service que sabe provocar falhas. Mantém o código de negócio
 * limpo: quem lê o [TransferService] enxerga a transferência, não o caos.
 */
@Component
class FailureSimulator {

    /**
     * `CRASH_AFTER_SEND`: o comando já foi "enviado" e o commit do banco não acontece.
     * Desde o passo 3, enviar é gravar na outbox, então o rollback leva a mensagem junto.
     */
    fun afterCommandsSent(transfer: Transfer) {
        if (transfer.simulation == Simulation.CRASH_AFTER_SEND) {
            log.warn("transferência {} simulate=CRASH_AFTER_SEND: caindo depois do send, antes do commit", transfer.id)
            throw SimulatedFailureException(transfer.id, "falha simulada depois do envio (transferência ${transfer.id})")
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(FailureSimulator::class.java)
    }
}
