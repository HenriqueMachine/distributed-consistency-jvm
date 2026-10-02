package workshop.saga.messaging.observability

import org.slf4j.MDC
import workshop.saga.contracts.Cid

/**
 * Coloca a transferência e o correlation id no MDC (Mapped Diagnostic Context) da thread
 * atual. Enquanto o bloco roda, **toda** linha de log da thread, inclusive as do Spring e
 * do Kafka, sai com `transferId=1042 cid=TRF-1042.DEB-a1`. É o que faz um grep contar a
 * história inteira (slide 44).
 */
object SagaContext {
    /** Chave do MDC com o id da transferência. */
    const val TRANSFER_ID = "transferId"

    /** Chave do MDC com o correlation id. */
    const val CID = "cid"

    /** Roda [block] com [transferId] e [cid] no MDC e, no fim, devolve o MDC como estava antes. */
    inline fun <T> with(transferId: Any, cid: Cid?, block: () -> T): T {
        val previousTransferId = MDC.get(TRANSFER_ID)
        val previousCid = MDC.get(CID)
        put(TRANSFER_ID, transferId.toString())
        put(CID, cid?.value)
        try {
            return block()
        } finally {
            put(TRANSFER_ID, previousTransferId)
            put(CID, previousCid)
        }
    }

    /** Roda [block] com outro [cid] (um filho, por exemplo), mantendo a transferência. */
    inline fun <T> withCid(cid: Cid?, block: () -> T): T {
        val previous = MDC.get(CID)
        put(CID, cid?.value)
        try {
            return block()
        } finally {
            put(CID, previous)
        }
    }

    /** Põe ou tira uma chave do MDC. */
    fun put(key: String, value: String?) {
        if (value == null) MDC.remove(key) else MDC.put(key, value)
    }
}
