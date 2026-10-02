package workshop.saga.transfer.infra.messaging

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import workshop.saga.contracts.AccountCommand
import workshop.saga.contracts.AccountDebited
import workshop.saga.contracts.DebitDeclined
import workshop.saga.contracts.DebitRefunded
import workshop.saga.contracts.InvalidPayloadException
import workshop.saga.contracts.Message
import workshop.saga.contracts.PixCommand
import workshop.saga.contracts.PixRejected
import workshop.saga.contracts.PixSettled
import workshop.saga.contracts.Topics
import workshop.saga.messaging.IncomingMessage
import workshop.saga.transfer.application.SagaOrchestrator
import workshop.saga.transfer.domain.saga.SagaEvent

/** Recebe as respostas dos participantes e as entrega ao orquestrador como eventos da saga. */
@Component
class SagaReplyListener(private val orchestrator: SagaOrchestrator) {

    /** Uma resposta do account-service ou do pix-service. */
    @KafkaListener(topics = [Topics.ACCOUNT_REPLIES, Topics.PIX_REPLIES])
    fun onReply(record: ConsumerRecord<String, String>) {
        val sagaMessage = IncomingMessage.read(record)
        orchestrator.onReply(sagaMessage, sagaMessage.message.toSagaEvent())
    }

    private fun Message.toSagaEvent(): SagaEvent = when (this) {
        is AccountDebited -> SagaEvent.AccountDebited(debitId)
        is DebitDeclined -> SagaEvent.DebitDeclined(reason)
        is DebitRefunded -> SagaEvent.DebitRefunded(debitId)
        is PixSettled -> SagaEvent.PixSettled(endToEndId)
        is PixRejected -> SagaEvent.PixRejected(reason)
        is AccountCommand, is PixCommand ->
            throw InvalidPayloadException("${this::class.simpleName} é um comando, não uma resposta")
    }
}
