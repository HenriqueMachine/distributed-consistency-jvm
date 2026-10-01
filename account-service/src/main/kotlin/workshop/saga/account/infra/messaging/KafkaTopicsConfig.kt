package workshop.saga.account.infra.messaging

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import workshop.saga.contracts.Topics

/** Tópico que o account-service consome: os comandos do orquestrador. */
@Configuration
class KafkaTopicsConfig {

    /** Comandos de débito e estorno. */
    @Bean
    fun accountCommands(): NewTopic = TopicBuilder.name(Topics.ACCOUNT_COMMANDS).partitions(Topics.PARTITIONS).build()
}
