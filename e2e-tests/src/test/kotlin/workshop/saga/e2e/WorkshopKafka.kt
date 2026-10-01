package workshop.saga.e2e

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration

/** Acesso direto ao Kafka (porta 9094), para ler DLTs e injetar mensagens. */
object WorkshopKafka {
    private val bootstrap = System.getenv("KAFKA_BOOTSTRAP") ?: "localhost:9094"

    /** Todos os registros de [topic] com a chave [key], lidos do começo, sem consumer group. */
    fun recordsWithKey(topic: String, key: String): List<ConsumerRecord<String, String>> =
        KafkaConsumer(consumerConfig(), StringDeserializer(), StringDeserializer()).use { consumer ->
            val partitions = consumer.partitionsFor(topic).map { TopicPartition(topic, it.partition()) }
            consumer.assign(partitions)
            consumer.seekToBeginning(partitions)
            val end = consumer.endOffsets(partitions)
            val records = mutableListOf<ConsumerRecord<String, String>>()
            while (partitions.any { consumer.position(it) < end.getValue(it) }) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add)
            }
            records.filter { it.key() == key }
        }

    /** Publica um registro cru, com headers em texto. */
    fun send(topic: String, key: String, value: String, headers: Map<String, String>) {
        KafkaProducer(producerConfig(), StringSerializer(), StringSerializer()).use { producer ->
            val record = ProducerRecord(topic, key, value).apply {
                headers.forEach { (name, v) -> headers().add(name, v.toByteArray()) }
            }
            producer.send(record).get()
        }
    }

    fun ConsumerRecord<String, String>.header(name: String): String? =
        headers().lastHeader(name)?.value()?.toString(Charsets.UTF_8)

    private fun consumerConfig() = mapOf<String, Any>(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
    )

    private fun producerConfig() = mapOf<String, Any>(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap)
}
