package workshop.saga.e2e

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import workshop.saga.e2e.WorkshopClient.participant
import workshop.saga.e2e.WorkshopClient.registerParticipant
import workshop.saga.e2e.WorkshopClient.uniqueKey

/** O cadastro dos participantes da apresentação. */
class ParticipantsTest {

    @Test
    fun `registers a participant with an initial balance`() {
        val key = uniqueKey("maria")

        val response = registerParticipant("Maria Souza", key, "1000.00")

        assertThat(response.statusCode()).isEqualTo(201)
        assertThat(participant(key)?.balance).isEqualByComparingTo("1000.00")
    }

    @Test
    fun `duplicate key is rejected with 409`() {
        val key = uniqueKey("joao")
        registerParticipant("João", key, "10.00")

        assertThat(registerParticipant("Outro João", key, "10.00").statusCode()).isEqualTo(409)
    }

    @Test
    fun `reserved key and invalid data are rejected with 400`() {
        assertThat(registerParticipant("X", "conta-encerrada", "10.00").statusCode()).isEqualTo(400)
        assertThat(registerParticipant("X", "Chave Com Espaço", "10.00").statusCode()).isEqualTo(400)
        assertThat(registerParticipant("X", uniqueKey("neg"), "-1.00").statusCode()).isEqualTo(400)
    }

    @Test
    fun `bia and henrique come in the initial seed`() {
        assertThat(participant("bia")).isNotNull()
        assertThat(participant("henrique")).isNotNull()
    }
}
