package workshop.saga.contracts

import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass

/**
 * Mensagem que nunca vai ser processada, não importa quantas vezes a gente tente:
 * JSON inválido, tipo desconhecido, campo faltando. Retry não ajuda (slide 36).
 */
class InvalidPayloadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Converte mensagens em JSON e de volta. O tipo viaja no header `messageType`
 * (nome simples da classe), e não dentro do JSON: o payload fica limpo para ler no Kafka UI.
 */
object MessageCodec {
    private val json = jacksonObjectMapper()

    /** Todas as classes concretas de [Message], descobertas a partir da hierarquia selada. */
    private val typesByName: Map<String, KClass<out Message>> =
        concreteSubclassesOf(Message::class).associateBy(::nameOf)

    /** Nome que vai no header `messageType`, ex.: `DebitAccount`. */
    fun typeOf(message: Message): String = nameOf(message::class)

    /** Payload JSON da mensagem, sem o tipo. */
    fun encode(message: Message): String = json.writeValueAsString(message)

    /**
     * Reconstrói a mensagem a partir do header `messageType` e do payload.
     * @throws InvalidPayloadException se o tipo for desconhecido ou o payload não servir.
     */
    fun decode(type: String?, payload: String?): Message {
        val kclass = typesByName[type]
            ?: throw InvalidPayloadException("tipo de mensagem desconhecido: $type")
        if (payload == null) throw InvalidPayloadException("mensagem $type sem payload")
        return try {
            json.readValue(payload, kclass.java)
        } catch (e: JacksonException) {
            throw InvalidPayloadException("payload inválido para $type: ${e.originalMessage}", e)
        }
    }

    // Classes de mensagem são sempre nomeadas (nunca anônimas), então simpleName existe.
    private fun nameOf(kclass: KClass<out Message>): String =
        checkNotNull(kclass.simpleName) { "mensagem sem nome: $kclass" }

    private fun concreteSubclassesOf(root: KClass<out Message>): List<KClass<out Message>> =
        if (root.isSealed) root.sealedSubclasses.flatMap { concreteSubclassesOf(it) } else listOf(root)
}
