# Passo 2 · Saga orquestrada

> Slides 21–25 · `git checkout passo-2`

## A ideia

Uma saga é um processo de negócio quebrado em passos locais, em que cada passo sabe se
desfazer. O `transfer-service` vira o **orquestrador**: envia comandos, recebe respostas e
decide o próximo passo numa máquina de estados.

```
CAMINHO FELIZ   CREATED → DEBIT_PENDING → PIX_PENDING → COMPLETED
COMPENSAÇÃO     PIX_PENDING → REFUNDING → CANCELLED      (conta destino encerrada)
```

Três ideias de produção entram juntas:

- **A saga é uma função pura** (slide 25): `decide(transferência, saga, evento, agora)`
  devolve o novo estado e os comandos. Sem Kafka, banco ou relógio lá dentro.
- **Compensar não é desfazer** (slide 26): o estorno é uma linha nova no extrato, e o
  débito continua lá.
- **Fatos não se apagam** (slide 27): cada transição vira uma linha em `saga_transitions`,
  com motivo, evento, cid e a versão do código que decidiu.

## O que mudou no código

| Onde | O quê |
|---|---|
| `contracts/` | As mensagens da saga como `sealed interface`: `DebitAccount`, `AccountDebited`, `SendPix`, `PixRejected`, `RefundDebit`… |
| `transfer-service/.../domain/saga/SagaStateMachine.kt` | **Leia este primeiro.** A saga inteira numa função pura |
| `transfer-service/.../application/SagaOrchestrator.kt` | Os efeitos: grava o estado, registra a transição, envia os comandos |
| `transfer-service/.../V2__saga_orchestration.sql` | `saga_transitions`: `revoke update, delete` + trigger que recusa UPDATE/DELETE/TRUNCATE |
| `buildSrc/.../spring-service-conventions.gradle.kts` | O git sha vai para o `build-info.properties`: é o `app_version` |
| `transfer-service/.../infra/scheduling/SagaTimeoutScanner.kt` | A cada 1 s, transforma prazo vencido em evento `TimedOut` |
| `account-service` | Debita (recusa sem saldo) e estorna; `GET /debits?transferId=` mostra o extrato |
| `pix-service` | SPI simulado; `conta-encerrada` é recusada; `GET /pix?transferId=` mostra o crédito |
| `shared-messaging/.../MessagePublisher.kt` | Publica **direto** no Kafka, dentro da transação do banco |

A máquina de estados não sabe que existe Kafka, banco ou Spring. Por isso a regra de negócio
inteira é testada em `SagaStateMachineTest` sem subir nenhum container.

## Rode

```bash
docker compose down && rm -rf logs && docker compose up -d
./gradlew bootRun --parallel
```

Use a collection do Postman (pasta "Passo 1–2 · Saga orquestrada") ou:

```bash
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -d '{"from": "ana", "to": "conta-encerrada", "amount": 150.00}'

grep -h '→' logs/*.log | sort
```

```
[transfer] 1042 CREATED → DEBIT_PENDING cmd=DebitAccount (transferência criada)
[account]  1042 debitado R$ 150,00 de ana debitId=d-1 → AccountDebited
[transfer] 1042 DEBIT_PENDING → PIX_PENDING cmd=SendPix (débito aprovado debitId=d-1)
[pix]      1042 conta destino encerrada chave=conta-encerrada → PixRejected
[transfer] 1042 PIX_PENDING → REFUNDING cmd=RefundDebit (Pix recusado: conta destino encerrada …)
[account]  1042 estornado R$ 150,00 para ana debitId=d-1 → DebitRefunded
[transfer] 1042 REFUNDING → CANCELLED cmd=- (estorno concluído debitId=d-1)
```

Por que a 1042 foi estornada? Pergunte ao histórico:

```bash
curl -s localhost:8081/transfers/1042/transitions
# [{"from":"CREATED","to":"DEBIT_PENDING","reason":"transferência criada","eventId":null,"cid":"TRF-1042","appVersion":"…"},
#  …
#  {"from":"PIX_PENDING","to":"REFUNDING","reason":"Pix recusado: conta destino encerrada …","eventId":"…","cid":"TRF-1042",…}]
```

E tente apagar um fato:

```bash
docker compose exec transfers-db psql -U workshop -d transfers -c "delete from saga_transitions"
# ERROR:  saga_transitions é só de inserção: DELETE não é permitido
```

## Quebre

Olhe o `TransferService.create`:

```kotlin
@Transactional
fun create(newTransfer: NewTransfer): TransferSummary {
    val transfer = transfers.insert(newTransfer)
    val saga = orchestrator.start(transfer)        // DebitAccount sai para o Kafka aqui
    failureSimulator.afterCommandsSent(transfer)
    return TransferSummary(transfer, saga.state)
} // o commit acontece AQUI, depois do send
```

Derrube a aplicação entre o `send` e o commit:

```bash
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -H 'X-Simulate: CRASH_AFTER_SEND' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'
# {"detail":"falha simulada depois do envio (transferência 1043)", "transferId":1043, ...}

curl -s -o /dev/null -w '%{http_code}\n' localhost:8081/transfers/1043   # 404
curl -s 'localhost:8082/debits?transferId=1043'                          # 1 débito
```

```
[transfer] transferência 1043 simulate=CRASH_AFTER_SEND: caindo depois do send, antes do commit
[account]  1043 debitado R$ 150,00 de ana debitId=d-2 → AccountDebited
[transfer] transferência 1043 não existe: AccountDebited ignorado
```

A Ana foi debitada por uma transferência que não existe (cenário A do slide 28).

Inverter a ordem (commit primeiro, `send` depois) não resolve: se a aplicação cair entre
os dois, a transferência fica em `DEBIT_PENDING` sem que nenhuma mensagem tenha saído
(cenário B). **Não existe ordem certa entre duas escritas em dois sistemas.**

Há um segundo defeito plantado aqui, que só aparece no passo 4: o timeout de 8 s do
débito é tratado como falha e dispara estorno (`⚠ QUEBRA passo-4` no `SagaStateMachine`).

## Por que o próximo passo existe

Precisamos que "gravar a transferência" e "mandar a mensagem" sejam **uma escrita só**. No
[passo 3](passo-3.md), a mensagem passa a ser gravada no mesmo banco, na mesma transação,
numa tabela `outbox`.
