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

/** Participante como o account-service devolve em `/participants`. */
data class ParticipantView(val pixKey: String, val name: String, val balance: BigDecimal)

/**
 * Cliente HTTP dos serviços em execução. As URLs podem ser trocadas por variável de
 * ambiente (TRANSFER_URL, ACCOUNT_URL), para rodar contra outro ambiente.
 */
object WorkshopClient {
    private val transferUrl = System.getenv("TRANSFER_URL") ?: "http://localhost:8081"
    private val accountUrl = System.getenv("ACCOUNT_URL") ?: "http://localhost:8082"
    private val http = HttpClient.newHttpClient()
    private val json = jacksonObjectMapper()

    fun createTransfer(from: String = "ana", to: String = "henrique", amount: String = "150.00"): TransferView {
        val response = post("$transferUrl/transfers", mapOf("from" to from, "to" to to, "amount" to BigDecimal(amount)))
        check(response.statusCode() == 201) { "POST /transfers devolveu ${response.statusCode()}: ${response.body()}" }
        return json.readValue(response.body())
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
