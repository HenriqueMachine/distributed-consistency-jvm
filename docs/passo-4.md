# Passo 4 · Idempotência

> Slides 28–32 · `git checkout passo-4`

## A ideia

| | Ordem | Garante |
|---|---|---|
| At-most-once | commit do offset → processa | nunca duplica, pode perder |
| **At-least-once** | processa → commit do offset | nunca perde, **pode duplicar** |
| Exactly-once | | só dentro do Kafka; fora dele é at-least-once + idempotência |

A outbox nos colocou em at-least-once dos dois lados. Exactly-once *de efeito* é
at-least-once mais **idempotência**: processar duas vezes tem o mesmo efeito que processar
uma.

Lembre do Pix (slide 34): a chave de idempotência nasce antes da 1ª tentativa e vai em todo
retry. Aqui, essa chave é o `transferId`.

## Duas camadas

| Camada | Pergunta | Como |
|---|---|---|
| 1 · mensagem | "Já processei este `eventId`?" | `insert into processed_messages`; conflito na PK = duplicata. **Na mesma transação do efeito** |
| 2 · negócio | "Já existe débito desta transferência?" | `unique (transfer_id)` em `debits` e `pix_transfers`; `unique (debit_id)` em `refunds` |

A camada 2 existe porque um **reenvio** do mesmo comando ganha um `eventId` novo e passa
pela camada 1. Quando a camada 2 encontra o efeito, o serviço **devolve o resultado
anterior** em vez de debitar de novo. É isso que o passo 5 vai usar.

E por que não usar partição + offset? Esse par só detecta a reentrega feita pelo broker
(rebalance, restart). O relay republicando e o orquestrador reenviando geram offsets novos.
O eventId e a chave de negócio pegam os três casos (slide 36).

## O que mudou no código

| Onde | O quê |
|---|---|
| `shared-messaging/.../inbox/Inbox.kt` | `firstDelivery(envelope)`: camada 1, `Propagation.MANDATORY` |
| `account-service/.../DebitService.kt` | Inbox + "já debitado → devolvendo resultado anterior" + estorno único |
| `pix-service/.../PixService.kt` | Inbox + "Pix já liquidado → devolvendo resultado anterior" |
| `transfer-service/.../SagaOrchestrator.kt` | `onReply` passa pela Inbox; `onTimeout` não passa (timeout não é mensagem) |
| `*/V*__processed_messages.sql`, `V*__one_*_per_transfer.sql` | As duas camadas no banco |
| `*/V*__outbox_available_at.sql` + `FailureSimulator` | Nova simulação `DEBIT_SLOW` |

## Rode

Recrie o ambiente: as migrations deste passo criam `unique (transfer_id)`, e os débitos
duplicados do passo 3 impediriam o account e o pix de subir.

```bash
docker compose down && rm -rf logs && docker compose up -d
./gradlew bootRun --parallel
```

Repita a quebra do passo 3:

```bash
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -H 'X-Simulate: DUPLICATE' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'

grep -h 'já processado' logs/*.log
```

```
[account]  1042 evento 121f… já processado — ignorado (simulate=DUPLICATE)
[pix]      1042 evento 4a4b… já processado — ignorado (simulate=DUPLICATE)
[transfer] 1042 evento 51ee… já processado — ignorado (simulate=DUPLICATE)
```

Agora `GET localhost:8082/debits?transferId=1042` mostra **um** débito.

## Quebre

O account-service debita em 0,2 s, mas a resposta se atrasa na rede. O orquestrador
espera 8 s pelo débito.

```bash
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -H 'X-Simulate: DEBIT_SLOW' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'
```

```
 0s  [transfer] 1043 CREATED → DEBIT_PENDING cmd=DebitAccount (transferência criada)
 0s  [account]  1043 debitado R$ 150,00 de ana debitId=d-2 → AccountDebited
 0s  [account]  1043 simulate=DEBIT_SLOW: resposta retida por 15s
 8s  [transfer] 1043 DEBIT_PENDING → REFUNDING cmd=RefundDebit (timeout 8s no débito: considerado falha, estornando)
 8s  [account]  1043 estornado R$ 150,00 para ana debitId=d-2 → DebitRefunded
 9s  [transfer] 1043 REFUNDING → CANCELLED cmd=- (estorno concluído debitId=d-2)
15s  [transfer] 1043 AccountDebited ignorado: saga já está em CANCELLED
```

O débito **deu certo**, mas o timeout foi lido como falha: a saga estornou e cancelou uma
transferência que ia passar. O Henrique não recebeu, e a Ana vê no extrato um débito e um
estorno que nunca deveriam ter acontecido. Olhe o `SagaStateMachine`, no ramo
`DEBIT_PENDING → TimedOut` (`⚠ QUEBRA passo-4`):

```kotlin
SagaEvent.TimedOut -> saga.moveTo(REFUNDING, reason = "… considerado falha, estornando", …, RefundDebit(…))
```

E se, em vez de estornar, a gente debitasse de novo? Pior ainda: seria o débito em dobro do
slide 34.

## Por que o próximo passo existe

> Timeout quer dizer "não sei". Não quer dizer "falhou".

No [passo 5](passo-5.md), o timeout leva a um estado `DEBIT_UNKNOWN` e o orquestrador
**pergunta de novo, com a mesma chave**. Como o account-service agora é idempotente, a
pergunta repetida é segura.
