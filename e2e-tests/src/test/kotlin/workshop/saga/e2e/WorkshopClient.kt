package workshop.saga.e2e

import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/** Transferência como o transfer-service devolve em `GET /transfers/{id}`. */
data class TransferView(val id: Long, val from: String, val to: String, val amount: BigDecimal, val state: String)

/** Extrato do account-service (`GET /debits?transferId=`). */
data class StatementView(val transferId: Long, val debits: List<Entry>, val refunds: List<Entry>) {
    data class Entry(val debitId: String, val amount: BigDecimal)
}

/** Crédito feito pelo pix-service (`GET /pix?transferId=`). */
data class PixCreditView(val to: String, val amount: BigDecimal, val endToEndId: String)

/** Uma linha de `saga_transitions` (`GET /transfers/{id}/transitions`). */
data class TransitionView(
    val from: String,
    val to: String,
    val reason: String,
    val eventId: String?,
    val cid: String,
    val appVersion: String,
)

/** Mensagem morta como o Mortician devolve em `/dead-letters`. */
data class DeadLetterView(
    val id: Long,
    val transferId: Long?,
    val status: String,
    val originalTopic: String,
    val messageType: String?,
    val error: String,
    val republishedBy: String?,
    val republishReason: String?,
)

/** Participante como o account-service devolve em `/participants`. */
data class ParticipantView(val pixKey: String, val name: String, val balance: BigDecimal)

/**
 * Cliente HTTP dos serviços em execução. As URLs podem ser trocadas por variável de
 * ambiente (TRANSFER_URL, ACCOUNT_URL), para rodar contra outro ambiente.
 */
object WorkshopClient {
    private val transferUrl = System.getenv("TRANSFER_URL") ?: "http://localhost:8081"
    private val accountUrl = System.getenv("ACCOUNT_URL") ?: "http://localhost:8082"
    private val pixUrl = System.getenv("PIX_URL") ?: "http://localhost:8083"
    private val morticianUrl = System.getenv("MORTICIAN_URL") ?: "http://localhost:8084"
    private val http = HttpClient.newHttpClient()
    private val json = jacksonObjectMapper()

    fun createTransfer(
        from: String,
        to: String = "henrique",
        amount: String = "150.00",
        simulate: String? = null,
    ): TransferView {
        val response = postTransfer(from, to, amount, simulate)
        check(response.statusCode() == 201) { "POST /transfers devolveu ${response.statusCode()}: ${response.body()}" }
        return json.readValue(response.body())
    }

    /** Para simulações que derrubam o request: devolve o `transferId` informado no erro. */
    fun createTransferExpectingFailure(from: String, simulate: String): Long {
        val response = postTransfer(from, "henrique", "150.00", simulate)
        check(response.statusCode() == 500) { "esperava 500, veio ${response.statusCode()}: ${response.body()}" }
        return json.readValue<Map<String, Any>>(response.body()).getValue("transferId").toString().toLong()
    }

    fun statement(transferId: Long): StatementView = json.readValue(get("$accountUrl/debits?transferId=$transferId").body())

    fun pixCredits(transferId: Long): List<PixCreditView> = json.readValue(get("$pixUrl/pix?transferId=$transferId").body())

    fun transitions(transferId: Long): List<TransitionView> =
        json.readValue(get("$transferUrl/transfers/$transferId/transitions").body())

    /** As mensagens mortas da transferência, das mais novas para as mais antigas. */
    fun deadLetters(transferId: Long): List<DeadLetterView> =
        json.readValue(get("$morticianUrl/dead-letters?transferId=$transferId").body())

    /** Republica uma mensagem morta; devolve o status HTTP. */
    fun republish(deadLetterId: Long, reason: String): Int =
        post("$morticianUrl/dead-letters/$deadLetterId/republish", mapOf("reason" to reason, "requestedBy" to "e2e")).statusCode()

    /** Derruba o SPI simulado para todos por [seconds] segundos. */
    fun startSpiOutage(seconds: Int) {
        val response = post("$pixUrl/spi/outage?seconds=$seconds", emptyMap<String, Any>())
        check(response.statusCode() == 200) { "POST /spi/outage devolveu ${response.statusCode()}" }
    }

    /** Traz o SPI de volta na hora. */
    fun endSpiOutage() {
        http.send(HttpRequest.newBuilder(URI.create("$pixUrl/spi/outage")).DELETE().build(), HttpResponse.BodyHandlers.ofString())
    }

    /** Estado do circuito `spi`: CLOSED, OPEN ou HALF_OPEN. */
    fun spiCircuitState(): String = json.readValue<Map<String, Any?>>(get("$pixUrl/spi").body()).getValue("circuit").toString()

    /** Cadastra um remetente só deste teste, para que os saldos não interfiram entre testes. */
    fun newSender(balance: String = "1000.00"): String {
        val key = uniqueKey("remetente")
        val response = registerParticipant("Remetente de teste", key, balance)
        check(response.statusCode() == 201) { "não cadastrou $key: ${response.body()}" }
        return key
    }

    fun transfer(id: Long): TransferView? {
        val response = get("$transferUrl/transfers/$id")
        return if (response.statusCode() == 404) null else json.readValue(response.body())
    }

    /** Cadastra um participante com chave única por teste; devolve o status HTTP e o corpo. */
    fun registerParticipant(name: String, pixKey: String, balance: String): HttpResponse<String> =
        post("$accountUrl/participants", mapOf("name" to name, "pixKey" to pixKey, "balance" to BigDecimal(balance)))

    fun participants(): List<ParticipantView> = json.readValue(get("$accountUrl/participants").body())

    fun participant(pixKey: String): ParticipantView? = participants().firstOrNull { it.pixKey == pixKey }

    /** Uma chave Pix que nenhum outro teste usa. */
    fun uniqueKey(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(8)}"

    /** Espera a saga chegar em [state]. */
    fun awaitState(id: Long, state: String, timeout: Duration = Duration.ofSeconds(30)): TransferView {
        await atMost timeout untilCallTo { transfer(id) } matches { it?.state == state }
        return checkNotNull(transfer(id))
    }

    private fun postTransfer(from: String, to: String, amount: String, simulate: String?): HttpResponse<String> =
        post(
            "$transferUrl/transfers",
            mapOf("from" to from, "to" to to, "amount" to BigDecimal(amount)),
            listOfNotNull(simulate?.let { "X-Simulate" to it }).toMap(),
        )

    private fun post(url: String, body: Any, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun get(url: String): HttpResponse<String> =
        http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString())
}
