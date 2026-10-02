package workshop.saga.contracts

/** Nomes dos tópicos e o roteamento de cada tipo de mensagem para o seu tópico. */
object Topics {
    /** Débitos e estornos, do orquestrador para o account-service. */
    const val ACCOUNT_COMMANDS = "account.commands"

    /** Respostas do account-service. */
    const val ACCOUNT_REPLIES = "account.replies"

    /** Envios de Pix, do orquestrador para o pix-service. */
    const val PIX_COMMANDS = "pix.commands"

    /** Respostas do pix-service. */
    const val PIX_REPLIES = "pix.replies"

    /** Todos os tópicos têm o mesmo número de partições; chave = transferId. */
    const val PARTITIONS = 3

    /**
     * Dead Letter Topic de um tópico (slide 41). O Spring Kafka publica na **mesma partição**
     * de origem, por isso a DLT precisa ter pelo menos [PARTITIONS] partições.
     */
    fun deadLetterOf(topic: String): String = "$topic.DLT"

    /** O tópico de cada mensagem. */
    fun of(message: Message): String = when (message) {
        is AccountCommand -> ACCOUNT_COMMANDS
        is AccountReply -> ACCOUNT_REPLIES
        is PixCommand -> PIX_COMMANDS
        is PixReply -> PIX_REPLIES
    }
}
