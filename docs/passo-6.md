# Passo 6 · Retry, DLT e circuit breaker

> Slides 36–40 · `git checkout passo-6`

## A ideia

| | Exemplo | O que fazer |
|---|---|---|
| **Erro transitório** | banco fora, rede, lock | tentar de novo, com calma: 1 s, 2 s, 4 s. Depois, DLT |
| **Erro permanente** | payload inválido, bug | retry não ajuda: direto para a DLT |
| **Dependência inteira fora** | o SPI caiu para todos | parar de insistir: **circuit breaker**. Nada vai para a DLT |

A **DLT** (Dead Letter Topic) é para onde vão as mensagens que morreram. No mundo das filas
(RabbitMQ, SQS), quem cuida disso é o broker. No Kafka, é a aplicação: o Spring Kafka
publica por você em `<tópico>.DLT`. A mensagem **sai da partição**, então a ordem por chave
se quebra para ela.

Já o **circuit breaker** cuida do caso em que o problema não é a mensagem, mas o terceiro:
quando passa de X% de falhas, o circuito abre e as chamadas param. De tempos em tempos, ele
deixa uma passar para testar. Sem isso, um SPI fora do ar encheria a DLT de mensagens que
só precisavam esperar.

## O que mudou no código

| Onde | O quê |
|---|---|
| `shared-messaging/.../errors/KafkaErrorHandlingConfig.kt` | `DefaultErrorHandler` + `ExponentialBackOffWithMaxRetries(3)` + `DeadLetterPublishingRecoverer`; `InvalidPayloadException` sem retry; `DependencyUnavailableException` retenta a cada 2 s, sem DLT |
| `contracts/.../Topics.kt` | `deadLetterOf(topic)` |
| `*/infra/messaging/KafkaTopicsConfig.kt` | Cada serviço cria as DLTs dos tópicos que consome, **com as mesmas 3 partições** |
| `transfer-service/.../SagaStateMachine.kt` | Todo passo pendente tem prazo: `PIX_PENDING` e `REFUNDING` também reenviam e, quando esgotam, vão para `NEEDS_ATTENTION` |
| `application.yml` (transfer) | `pix: 12s`: o prazo do Pix cabe o retry de 7 s do consumidor |
| `pix-service/.../infra/spi/SpiGateway.kt` | Toda chamada ao SPI passa pelo circuit breaker `spi` (Resilience4j) |
| `pix-service/.../infra/spi/SpiCircuitBreakerListener.kt` | Circuito OPEN → `pause()` no consumidor `pix`; HALF_OPEN/CLOSED → `resume()` |
| `pix-service/.../application.yml` | `resilience4j.circuitbreaker.instances.spi.*`, como no slide 39 |
| `pix-service/.../infra/web/SpiController.kt` | `POST /spi/outage?seconds=15`, `DELETE /spi/outage`, `GET /spi` |

Por que as mesmas partições? O `DeadLetterPublishingRecoverer` publica na **mesma
partição** de origem. Uma DLT com 1 partição não teria a partição 2, e a mensagem que
deveria ser guardada falharia de novo.

A compensação também tem prazo. Se o `RefundDebit` não for confirmado, a saga reenvia e,
quando as tentativas acabam, para em `NEEDS_ATTENTION`. Não existe compensação da
compensação (slide 26).

## Rode

```bash
docker compose down && rm -rf logs && docker compose up -d
./gradlew bootRun --parallel
```

### Retry e DLT

```bash
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -H 'X-Simulate: PIX_CRASH' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'
```

```
 0s  [pix]      1042 simulate=PIX_CRASH: falha ao processar o Pix
 1s  [pix]      1042 simulate=PIX_CRASH: falha ao processar o Pix      ← retry 1
 3s  [pix]      1042 simulate=PIX_CRASH: falha ao processar o Pix      ← retry 2
 7s  [pix]      1042 simulate=PIX_CRASH: falha ao processar o Pix      ← retry 3
 7s  [pix]      1042 falha tentativa=3 → pix.commands.DLT: falha ao processar o Pix (simulada) …
12s  [transfer] 1042 PIX_PENDING → PIX_PENDING cmd=SendPix (timeout 12s → sem resposta do Pix, reenviando com a mesma chave tentativa=2)
     …   (mais uma rodada de retry e DLT, e mais outra)
36s  [transfer] 1042 PIX_PENDING → NEEDS_ATTENTION cmd=- (timeout 12s sem resposta após 3 tentativas)
```

No Kafka UI (http://localhost:8080), em Topics → `pix.commands.DLT` → Messages, cada
mensagem traz o payload original e headers que explicam a morte dela:

| Header | Conteúdo |
|---|---|
| `kafka_dlt-original-topic` / `-partition` / `-offset` | de onde ela veio |
| `kafka_dlt-exception-cause-fqcn` | a exceção real (`PixProcessingException`) |
| `kafka_dlt-exception-message` | a mensagem de erro |
| `kafka_dlt-exception-stacktrace` | a stack trace inteira |

**Erro permanente.** No Kafka UI, em `pix.commands` → *Produce Message*, publique `{oops`
com os headers `messageType: SendPix` e `messageId: 6f1c0b2e-0000-0000-0000-000000000000`.
A mensagem vai `falha permanente, sem retry → pix.commands.DLT`, sem as esperas de 1 s, 2 s
e 4 s.

### Circuit breaker

```bash
curl -s -X POST 'localhost:8083/spi/outage?seconds=15'
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'
watch -n1 curl -s localhost:8083/spi        # CLOSED → OPEN → HALF_OPEN → CLOSED
```

```
 0s  [pix] SPI fora do ar por 15s
 1s  [pix] 1043 SPI indisponível              ← a cada 2 s: dependência fora, sem DLT
 …
 9s  [pix] 1043 SPI indisponível
 9s  [pix] circuito spi CLOSED → OPEN: pausando o consumidor pix
19s  [pix] circuito spi OPEN → HALF_OPEN: retomando o consumidor pix
19s  [pix] circuito spi HALF_OPEN → CLOSED: retomando o consumidor pix
19s  [pix] 1043 Pix liquidado R$ 150,00 para henrique endToEndId=E1043… → PixSettled
```

Enquanto o circuito está aberto, abra Consumers → `pix-service` no Kafka UI: o **lag**
cresce. É o consumidor ditando o ritmo (slide 42). Nada foi para a DLT.

## Quebre

O sistema agora sobrevive. Mas olhe de novo para a 1042, que terminou em
`NEEDS_ATTENTION`. Onde está a mensagem que morreu?

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic pix.commands.DLT \
  --from-beginning --property print.key=true --property print.headers=true
```

- Ela **só existe na DLT**. Nenhum serviço a guarda, lista ou explica.
- **Ninguém foi avisado**: o único rastro é uma linha de log.
- Reprocessar é "na mão": copiar o payload do console e publicar de novo, sem registro de
  quem fez, quando e por quê. E se for publicado direto no Kafka, é escrita dupla de novo.

> Toda DLT tem dono, alerta e runbook. Sem isso, é lixeira.

## Por que o próximo passo existe

A DLT precisa de um **dono**. No [passo 7](passo-7.md), o `mortician-service` consome todas
as DLTs, guarda cada mensagem com o erro e expõe listar, investigar e republicar.
