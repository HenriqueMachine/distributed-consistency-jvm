package workshop.saga.pix.infra.messaging

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/** Tópico que o pix-service consome: os comandos do orquestrador. */
@Configuration
class KafkaTopicsConfig {

    /** Comandos de envio de Pix. */
    @Bean
    fun pixCommands(): NewTopic = TopicBuilder.name("pix.commands").partitions(3).build()
}
