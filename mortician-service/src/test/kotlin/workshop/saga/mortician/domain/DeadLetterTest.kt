package workshop.saga.mortician.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeadLetterTest {

    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val dead = DeadLetter(
        id = 7, deadLetterTopic = "pix.commands.DLT", originalTopic = "pix.commands", key = "1042",
        messageId = null, messageType = "SendPix", payload = "{}", error = "PixProcessingException: falha",
        transferId = 1042, cid = "TRF-1042.PIX-c3", status = DeadLetterStatus.NEW, receivedAt = now,
    )

    @Test
    fun `rescue records who, why and when`() {
        val rescued = dead.republish(by = "henrique", reason = "bug corrigido no deploy 42", at = now)

        assertEquals(DeadLetterStatus.REPUBLISHED, rescued.status)
        assertEquals(DeadLetter.Rescue("henrique", "bug corrigido no deploy 42", now), rescued.rescue)
    }

    @Test
    fun `does not republish twice nor without a reason`() {
        val rescued = dead.republish("henrique", "corrigido", now)

        assertFailsWith<AlreadyRepublishedException> { rescued.republish("outra", "de novo", now) }
        assertFailsWith<IllegalArgumentException> { dead.republish("henrique", " ", now) }
    }
}
