package workshop.saga.pix.infra.messaging

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import workshop.saga.contracts.Topics

/** Tópico que o pix-service consome: os comandos do orquestrador. */
@Configuration
class KafkaTopicsConfig {

    /** Comandos de envio de Pix. */
    @Bean
    fun pixCommands(): NewTopic = TopicBuilder.name(Topics.PIX_COMMANDS).partitions(Topics.PARTITIONS).build()

    /** DLT de `pix.commands`, com as mesmas partições: o recoverer publica na partição de origem. */
    @Bean
    fun pixCommandsDeadLetter(): NewTopic =
        TopicBuilder.name(Topics.deadLetterOf(Topics.PIX_COMMANDS)).partitions(Topics.PARTITIONS).build()
}
