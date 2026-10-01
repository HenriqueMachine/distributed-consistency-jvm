package workshop.saga.transfer.infra.messaging

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/**
 * Tópicos que o transfer-service consome: as respostas dos participantes da saga.
 * Cada serviço declara os tópicos que lê; o KafkaAdmin os cria na subida.
 */
@Configuration
class KafkaTopicsConfig {

    /** Respostas do account-service. */
    @Bean
    fun accountReplies(): NewTopic = TopicBuilder.name("account.replies").partitions(3).build()

    /** Respostas do pix-service. */
    @Bean
    fun pixReplies(): NewTopic = TopicBuilder.name("pix.replies").partitions(3).build()
}
