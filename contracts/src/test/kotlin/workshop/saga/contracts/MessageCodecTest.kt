package workshop.saga.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageCodecTest {

    @Test
    fun `round trip preserves the message`() {
        val original = DebitAccount(transferId = 1042, from = "ana", amountInCents = 15_000)

        val decoded = MessageCodec.decode(MessageCodec.typeOf(original), MessageCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `unknown type and invalid payload become InvalidPayloadException`() {
        assertFailsWith<InvalidPayloadException> { MessageCodec.decode("Nope", "{}") }
        assertFailsWith<InvalidPayloadException> { MessageCodec.decode("DebitAccount", "{not json") }
        assertFailsWith<InvalidPayloadException> { MessageCodec.decode("DebitAccount", """{"transferId":1}""") }
    }

    @Test
    fun `each message knows its topic`() {
        assertEquals(Topics.ACCOUNT_COMMANDS, Topics.of(RefundDebit(1042)))
        assertEquals(Topics.PIX_REPLIES, Topics.of(PixRejected(1042, "conta destino encerrada")))
    }
}
