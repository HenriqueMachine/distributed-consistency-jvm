package workshop.saga.messaging.observability

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.RecordInterceptor
import org.springframework.stereotype.Component
import workshop.saga.contracts.MessageHeaders

/**
 * Antes de cada mensagem consumida, põe `transferId` (a chave) e `cid` (o header `x-cid`)
 * no MDC; depois, tira. Vale para o listener, para os retries e para a ida à DLT. O Spring
 * Boot liga este interceptor a todos os `@KafkaListener` do serviço.
 */
@Component
class SagaContextRecordInterceptor : RecordInterceptor<Any, Any> {

    override fun intercept(record: ConsumerRecord<Any, Any>, consumer: Consumer<Any, Any>): ConsumerRecord<Any, Any> {
        SagaContext.put(SagaContext.TRANSFER_ID, record.key()?.toString())
        SagaContext.put(SagaContext.CID, record.headers().lastHeader(MessageHeaders.CID)?.value()?.toString(Charsets.UTF_8))
        return record
    }

    override fun afterRecord(record: ConsumerRecord<Any, Any>, consumer: Consumer<Any, Any>) = clear()

    /**
     * Rede de segurança: se o próprio tratamento de erro falhar, `afterRecord` pode não
     * rodar. O container sempre chama este método antes de voltar a buscar mensagens,
     * então uma transferência nunca "vaza" para as linhas de log da próxima.
     */
    override fun clearThreadState(consumer: Consumer<*, *>) = clear()

    private fun clear() {
        SagaContext.put(SagaContext.TRANSFER_ID, null)
        SagaContext.put(SagaContext.CID, null)
    }
}
