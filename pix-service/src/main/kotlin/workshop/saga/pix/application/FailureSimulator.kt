package workshop.saga.pix.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import workshop.saga.contracts.SagaMessage
import workshop.saga.contracts.Simulation

/**
 * Bug que derruba o processamento desta mensagem, mas parece passageiro: quem o recebe não
 * tem como saber se tentar de novo vai adiantar.
 */
class PixProcessingException(message: String) : RuntimeException(message)

/** Único lugar do pix-service que sabe provocar falhas. */
@Component
class FailureSimulator {

    /** `PIX_CRASH`: toda tentativa de enviar o Pix desta transferência falha. */
    fun beforeSend(request: SagaMessage) {
        if (request.simulation == Simulation.PIX_CRASH) {
            log.warn("simulate=PIX_CRASH: falha ao processar o Pix")
            throw PixProcessingException("falha ao processar o Pix (simulada) da transferência ${request.transferId}")
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(FailureSimulator::class.java)
    }
}
