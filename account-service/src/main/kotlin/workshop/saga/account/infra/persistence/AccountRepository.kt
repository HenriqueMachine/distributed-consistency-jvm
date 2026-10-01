package workshop.saga.account.infra.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import workshop.saga.account.domain.Account
import workshop.saga.contracts.Money
import java.sql.ResultSet

/** Tabela `accounts`: uma conta por chave Pix. */
@Repository
class AccountRepository(private val jdbc: JdbcClient) {

    /** Grava a conta; `false` se a chave já existir. */
    fun insertIfAbsent(account: Account): Boolean =
        jdbc.sql(
            """
            insert into accounts (pix_key, name, balance_cents)
            values (:pixKey, :name, :balance)
            on conflict (pix_key) do nothing
            """,
        )
            .param("pixKey", account.pixKey)
            .param("name", account.name)
            .param("balance", account.balance.cents)
            .update() == 1

    /** Lê a conta e trava a linha até o fim da transação; nula se a chave não existir. */
    fun lockByKey(pixKey: String): Account? =
        jdbc.sql("select pix_key, name, balance_cents from accounts where pix_key = :pixKey for update")
            .param("pixKey", pixKey)
            .query { rs, _ -> rs.toAccount() }
            .optional()
            .orElse(null)

    /** Grava o novo saldo. */
    fun updateBalance(account: Account) {
        jdbc.sql("update accounts set balance_cents = :balance where pix_key = :pixKey")
            .param("pixKey", account.pixKey)
            .param("balance", account.balance.cents)
            .update()
    }

    /** Todas as contas, em ordem de cadastro. */
    fun findAll(): List<Account> =
        jdbc.sql("select pix_key, name, balance_cents from accounts order by created_at, pix_key")
            .query { rs, _ -> rs.toAccount() }
            .list()

    private fun ResultSet.toAccount() =
        Account(getString("pix_key"), getString("name"), Money(getLong("balance_cents")))
}
