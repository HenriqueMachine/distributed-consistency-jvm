package workshop.saga.contracts

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Valor monetário em centavos, para nunca somar ponto flutuante. Compartilhado por todos
 * os serviços: saldo, valor da transferência, débito e crédito.
 */
@JvmInline
value class Money(val cents: Long) {
    /** Soma em centavos. */
    operator fun plus(other: Money) = Money(cents + other.cents)

    /** Subtrai em centavos. */
    operator fun minus(other: Money) = Money(cents - other.cents)

    /** Compara em centavos: `saldo < valor`. */
    operator fun compareTo(other: Money) = cents.compareTo(other.cents)

    /** Valor em reais, para JSON e para gente. */
    fun toDecimal(): BigDecimal = BigDecimal.valueOf(cents, 2)

    /** Formato do extrato do cliente: `R$ 150,00`. */
    override fun toString(): String = BRL.format(toDecimal()).replace('\u00a0', ' ')

    companion object {
        private val BRL = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"))

        /**
         * Converte reais (ex.: `150.00`) em centavos. Recusa frações de centavo e valores que
         * não cabem em centavos `Long`, em vez de arredondar ou estourar em silêncio.
         */
        fun of(amount: BigDecimal): Money {
            require(amount.stripTrailingZeros().scale() <= 2) { "valor com mais de 2 casas decimais: $amount" }
            val cents = amount.movePointRight(2).toBigIntegerExact()
            require(cents.bitLength() < Long.SIZE_BITS) { "valor grande demais: $amount" }
            return Money(cents.toLong())
        }
    }
}
