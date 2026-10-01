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

    /** Todas as contas, em ordem de cadastro. */
    fun findAll(): List<Account> =
        jdbc.sql("select pix_key, name, balance_cents from accounts order by created_at, pix_key")
            .query { rs, _ -> rs.toAccount() }
            .list()

    private fun ResultSet.toAccount() =
        Account(getString("pix_key"), getString("name"), Money(getLong("balance_cents")))
}
