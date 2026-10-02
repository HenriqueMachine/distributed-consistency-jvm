package workshop.saga.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CidTest {

    @Test
    fun `the root is the transfer id and each child appends a segment`() {
        val root = Cid.root(1042)
        val pix = root.child("PIX")
        val spi = pix.child("SPI")

        assertEquals("TRF-1042", root.value)
        assertTrue(Regex("""TRF-1042\.PIX-[0-9a-f]{2}""").matches(pix.value), pix.value)
        assertTrue(spi.value.startsWith("${pix.value}.SPI-"), spi.value)
    }
}
