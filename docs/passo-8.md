# Passo 8 · Observabilidade

> Slides 42–43 · `git checkout passo-8`

## A ideia

> Aceite: "o que aconteceu com a 1042?" se responde com um grep.

Quatro peças:

| Peça | Onde |
|---|---|
| **Correlation id em árvore** | `x-cid` no header de toda mensagem; cada comando e cada chamada ganha um filho |
| **Logs estruturados** | `transferId=1042 cid=TRF-1042.DEB-a1` em toda linha com contexto; perfil `json` com ECS |
| **Transições** | todo `de → para` vira log (com motivo, tentativa e evento) e linha em `saga_transitions` |
| **Métricas** | transições, sagas por estado (UNKNOWN, NEEDS_ATTENTION), mensagens na DLT, circuito do SPI |

### Correlation id em árvore (slide 45)

Quem produz a próxima mensagem acrescenta um segmento ao id. O id conta o caminho:

```
TRF-1042
├─ TRF-1042.DEB-a1              DebitAccount #1
├─ TRF-1042.DEB-b7              DebitAccount #2 (reenvio depois do timeout)
└─ TRF-1042.PIX-c3              SendPix
   ├─ TRF-1042.PIX-c3.SPI-3f    chamada ao SPI
   └─ TRF-1042.PIX-c3.RPB-9d    resgate pelo Mortician
```

`grep TRF-1042` traz a saga inteira, e a tentativa 1 do débito se separa da tentativa 2.

## O que mudou no código

| Onde | O quê |
|---|---|
| `contracts/.../Cid.kt` | `Cid.root(1042)` e `child("PIX")` |
| `contracts/.../Envelope.kt` | `cid` + `reply(message)`: a resposta herda o cid do comando |
| `shared-messaging/.../observability/SagaContext.kt` | `with(transferId, cid) { … }`: põe no MDC e devolve como estava |
| `shared-messaging/.../observability/SagaContextRecordInterceptor.kt` | MDC em todo `@KafkaListener`, **inclusive retries e ida à DLT** |
| `shared-messaging/.../outbox/*` + `V*__outbox_cid.sql` | O cid é gravado na outbox e vai no header `x-cid` |
| `transfer-service/.../SagaOrchestrator.kt` | Um filho por comando; `saga_transitions.cid` é o cid de quem causou |
| `pix-service/.../PixService.kt` | A chamada ao SPI roda com o cid `….SPI-xx` |
| `mortician-service/.../DeadLetterService.kt` | O resgate republica com `….RPB-xx` |
| `*/application.yml` | `logging.pattern.correlation`: prefixa `transferId=… cid=…` quando há contexto |
| `*/application-json.yml` | Perfil `json`: `logging.structured.format.console: ecs` |
| `transfer-service/.../infra/metrics/SagaMetrics.kt` | `saga.transitions` (contada depois do commit) e `saga.state` |
| `shared-messaging/.../errors/KafkaErrorHandlingConfig.kt` | `saga.dlt.messages` |
| Todas as mensagens de log | Sem o id no texto: quem o põe é o MDC, sempre do mesmo jeito |

## Rode

```bash
docker compose down && rm -rf logs && docker compose up -d
./gradlew bootRun --parallel

curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -H 'X-Simulate: DEBIT_SLOW' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'

grep -h TRF-1042 logs/*.log | sort
```

```
transfer  transferId=1042 cid=TRF-1042        transferência criada ana → henrique valor=R$ 150,00
transfer  transferId=1042 cid=TRF-1042        saga CREATED → DEBIT_PENDING cmd=DebitAccount tentativa=1 eventId=- motivo="transferência criada"
account   transferId=1042 cid=TRF-1042.DEB-b8 débito aprovado R$ 150,00 de ana debitId=d-1 → AccountDebited
account   transferId=1042 cid=TRF-1042.DEB-b8 simulate=DEBIT_SLOW: resposta retida por 15s
transfer  transferId=1042 cid=TRF-1042        saga DEBIT_PENDING → DEBIT_UNKNOWN cmd=DebitAccount tentativa=2 eventId=- motivo="timeout 8s → não sei se debitou, …"
account   transferId=1042 cid=TRF-1042.DEB-c8 já debitado debitId=d-1 → devolvendo resultado anterior
transfer  transferId=1042 cid=TRF-1042.DEB-c8 saga DEBIT_UNKNOWN → PIX_PENDING cmd=SendPix tentativa=1 eventId=7fe7 motivo="débito aprovado debitId=d-1"
pix       transferId=1042 cid=TRF-1042.PIX-76.SPI-43 SPI liquidou endToEndId=E1042d3d794
pix       transferId=1042 cid=TRF-1042.PIX-76 Pix liquidado R$ 150,00 para henrique endToEndId=E1042d3d794 → PixSettled
transfer  transferId=1042 cid=TRF-1042.PIX-76 saga PIX_PENDING → COMPLETED cmd=- tentativa=- eventId=cba1 motivo="Pix liquidado …"
transfer  transferId=1042 cid=TRF-1042.DEB-b8 AccountDebited ignorado: saga já está em COMPLETED
```

> Ao vivo, `tail -f logs/*.log | grep TRF-1042` já sai na ordem. Rodando no Docker
> (perfil `apps`), `docker compose logs -f | grep TRF-1042` funciona do mesmo jeito.

Repare na última linha. A resposta **atrasada** da primeira tentativa (`DEB-b8`) chega
depois de tudo e é ignorada. O cid mostra que ela é da tentativa 1, e não da 2 (`DEB-c8`).

### Até o framework fala a transferência

As linhas do Spring Kafka, que no passo 7 não diziam de quem eram, agora dizem:

```
WARN  transferId=1043 cid=TRF-1043.PIX-a4 FailureSimulator              : simulate=PIX_CRASH: falha ao processar o Pix
INFO  transferId=1043 cid=TRF-1043.PIX-a4 KafkaMessageListenerContainer : Record in retry and not yet recovered
ERROR transferId=1043 cid=TRF-1043.PIX-a4 KafkaErrorHandlingConfig      : falha tentativa=3 → pix.commands.DLT: …
```

O MDC é da thread, e o interceptor o preenche antes de qualquer código rodar para aquela
mensagem: listener, retry e recoverer.

### Logs em JSON (ECS)

```bash
SPRING_PROFILES_ACTIVE=json ./gradlew bootRun --parallel
```

```json
{"@timestamp":"…","log":{"level":"INFO","logger":"workshop.saga.account.application.DebitService"},
 "service":{"name":"account-service"},"message":"débito aprovado R$ 150,00 de ana debitId=d-1 → AccountDebited",
 "transferId":"1042","cid":"TRF-1042.DEB-b8", …}
```

### Métricas

```bash
curl -s 'localhost:8081/actuator/metrics/saga.state?tag=state:NEEDS_ATTENTION'
curl -s 'localhost:8081/actuator/metrics/saga.transitions?tag=to:CANCELLED'
curl -s localhost:8083/actuator/metrics/saga.dlt.messages
curl -s localhost:8083/actuator/circuitbreakers
```

O lag por consumer group aparece no Kafka UI, em Consumers.

## Checklist para adotar no time

- [ ] Toda escrita que gera mensagem passa pelo outbox → `MessagePublisher` (passo 3)
- [ ] Todo consumidor é idempotente: eventId + chave de negócio → `Inbox` + `unique` (passo 4)
- [ ] Chave da mensagem = id do agregado → `transferId` (passo 2)
- [ ] Timeout leva a UNKNOWN, nunca a compensação automática → `SagaStateMachine` (passo 5)
- [ ] Compensação também é idempotente e também pode falhar → `RefundDebit` com prazo (passo 6)
- [ ] Retry só para erro transitório; o resto vai para a DLT → `KafkaErrorHandlingConfig` (passo 6)
- [ ] Toda DLT tem dono, alerta e runbook → Mortician + `saga.dlt.messages` (passos 7 e 8)
- [ ] cid em árvore no header, no MDC e em todo log (passo 8)
- [ ] Contratos de mensagem versionados → `contracts/` (aqui, um módulo compartilhado)
- [ ] Lag e sagas paradas no dashboard → Kafka UI + `saga.state`

## Próximo

[Laboratório final](lab.md): reconstrua a história de cada transação só pelos logs.
