package workshop.saga.account.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import workshop.saga.account.domain.Account
import workshop.saga.account.domain.NewParticipant
import workshop.saga.account.infra.persistence.AccountRepository

/** A chave Pix já pertence a outro participante. */
class PixKeyAlreadyTakenException(pixKey: String) : RuntimeException("a chave $pixKey já está cadastrada")

/**
 * Cadastro dos participantes da apresentação: cada pessoa da sala vira uma conta, e as
 * transferências passam a acontecer entre elas.
 */
@Service
class ParticipantService(private val accounts: AccountRepository) {

    /** Abre a conta do participante com o saldo inicial informado. */
    @Transactional
    fun register(participant: NewParticipant): Account {
        val account = participant.toAccount()
        if (!accounts.insertIfAbsent(account)) throw PixKeyAlreadyTakenException(account.pixKey)
        log.info("participante cadastrado {} chave={} saldo={}", account.name, account.pixKey, account.balance)
        return account
    }

    /** Todas as contas, com o saldo atual. */
    @Transactional(readOnly = true)
    fun list(): List<Account> = accounts.findAll()

    private companion object {
        val log = LoggerFactory.getLogger(ParticipantService::class.java)
    }
}
