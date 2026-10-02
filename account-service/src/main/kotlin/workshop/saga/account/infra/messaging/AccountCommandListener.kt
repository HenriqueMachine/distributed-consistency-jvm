package workshop.saga.account.infra.messaging

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import workshop.saga.account.application.DebitService
import workshop.saga.contracts.DebitAccount
import workshop.saga.contracts.InvalidPayloadException
import workshop.saga.contracts.RefundDebit
import workshop.saga.contracts.Topics
import workshop.saga.messaging.IncomingMessage

/** Recebe os comandos do orquestrador e os entrega ao caso de uso certo. */
@Component
class AccountCommandListener(private val debitService: DebitService) {

    /** Um `DebitAccount` ou um `RefundDebit`. */
    @KafkaListener(topics = [Topics.ACCOUNT_COMMANDS])
    fun onCommand(record: ConsumerRecord<String, String>) {
        val sagaMessage = IncomingMessage.read(record)
        when (val command = sagaMessage.message) {
            is DebitAccount -> debitService.debit(command, sagaMessage)
            is RefundDebit -> debitService.refund(command, sagaMessage)
            else -> throw InvalidPayloadException("${sagaMessage.type} não é um comando de conta")
        }
    }
}
