package workshop.saga.transfer.infra.messaging

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import workshop.saga.contracts.Topics

/**
 * Tópicos que o transfer-service consome: as respostas dos participantes da saga.
 * Cada serviço declara os tópicos que lê, e as DLTs deles; o KafkaAdmin os cria na subida.
 */
@Configuration
class KafkaTopicsConfig {

    /** Respostas do account-service. */
    @Bean
    fun accountReplies(): NewTopic = TopicBuilder.name(Topics.ACCOUNT_REPLIES).partitions(Topics.PARTITIONS).build()

    /** Respostas do pix-service. */
    @Bean
    fun pixReplies(): NewTopic = TopicBuilder.name(Topics.PIX_REPLIES).partitions(Topics.PARTITIONS).build()

    /** DLT de `account.replies`, com as mesmas partições: o recoverer publica na partição de origem. */
    @Bean
    fun accountRepliesDeadLetter(): NewTopic =
        TopicBuilder.name(Topics.deadLetterOf(Topics.ACCOUNT_REPLIES)).partitions(Topics.PARTITIONS).build()

    /** DLT de `pix.replies`, com as mesmas partições: o recoverer publica na partição de origem. */
    @Bean
    fun pixRepliesDeadLetter(): NewTopic =
        TopicBuilder.name(Topics.deadLetterOf(Topics.PIX_REPLIES)).partitions(Topics.PARTITIONS).build()
}
