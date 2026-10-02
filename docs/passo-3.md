# Passo 3 · Outbox

> Slides 7 e 26 · `git checkout passo-3`

## A ideia

Pense no e-mail no modo avião: você clica em enviar, a mensagem não sai, mas fica salva na
caixa de saída. Quando a conexão volta, ela vai sozinha.

1. **Grava junto**: a transferência e a mensagem vão para a tabela `outbox` no mesmo commit.
2. **Alguém envia**: um relay lê a outbox e publica no Kafka.
3. **Marca como enviada**: se cair antes de marcar, envia de novo quando voltar.

Com isso a escrita dupla vira uma escrita só, no banco, e o Kafka fica para depois.

## O que mudou no código

| Onde | O quê |
|---|---|
| `shared-messaging/.../MessagePublisher.kt` | Mesma API do passo 2, mas agora **grava na outbox**. Com `Propagation.MANDATORY`, chamar sem transação falha na hora |
| `shared-messaging/.../outbox/OutboxRelay.kt` | `@Scheduled` a cada 200 ms: lê as pendentes (`for update skip locked`), publica e marca `published_at` |
| `*/db/migration/V*__outbox.sql` | Cada serviço tem a própria outbox, no próprio banco |
| `contracts/.../Simulation.kt` | Nova simulação `DUPLICATE` |

Nenhum caso de uso mudou: `TransferService`, `DebitService` e `PixService` continuam
chamando `publisher.publish(...)`. Para conferir:

```bash
git diff passo-2 passo-3 -- '*.kt' '*.sql'
```

Todas as escritas que geram mensagem passam pela outbox, inclusive as respostas do account
e do pix.

## Rode

```bash
docker compose down && rm -rf logs && docker compose up -d
./gradlew bootRun --parallel
```

Repita a quebra do passo 2:

```bash
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -H 'X-Simulate: CRASH_AFTER_SEND' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'
# {"detail":"falha simulada depois do envio (transferência 1042)", "transferId":1042, ...}

curl -s 'localhost:8082/debits?transferId=1042'
# {"transferId":1042,"debits":[],"refunds":[]}
```

Desta vez o rollback levou a transferência e a mensagem juntas, e ninguém foi debitado.

## Quebre

O relay publica primeiro e só depois marca `published_at`. O que acontece se ele cair entre
as duas coisas?

```bash
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -H 'X-Simulate: DUPLICATE' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'
```

```
[transfer] 1043 CREATED → DEBIT_PENDING cmd=DebitAccount (transferência criada)
[transfer] relay simulate=DUPLICATE: DebitAccount messageId=8c4f… publicado 2× (queda antes de marcar published_at)
[account]  1043 debitado R$ 150,00 de ana debitId=d-1 → AccountDebited
[account]  1043 debitado R$ 150,00 de ana debitId=d-2 → AccountDebited
[transfer] 1043 DEBIT_PENDING → PIX_PENDING cmd=SendPix (débito aprovado debitId=d-1)
[transfer] 1043 AccountDebited ignorado: saga já está em PIX_PENDING
[pix]      1043 Pix liquidado R$ 150,00 para henrique endToEndId=E1043… → PixSettled
[pix]      1043 Pix liquidado R$ 150,00 para henrique endToEndId=E1043… → PixSettled
```

```bash
curl -s 'localhost:8082/debits?transferId=1043'   # 2 débitos
curl -s localhost:8082/participants               # a Ana perdeu R$ 300,00
```

A Ana foi **debitada duas vezes**, e o Henrique recebeu dois Pix. O orquestrador se protegeu
sozinho, porque a máquina de estados ignora respostas que chegam fora de hora. Os
participantes, não.

No Kafka UI, abra `account.commands`: as duas mensagens têm o **mesmo `messageId`** e
offsets diferentes. Essa é a pista para o próximo passo.

> A outbox garante que nada se perde. Não garante que nada se repete.

## Por que o próximo passo existe

At-least-once é o padrão do Spring Kafka e da outbox (slide 30): vamos receber duplicatas,
sempre. No [passo 4](passo-4.md), todo consumidor passa a ser **idempotente**: processar
duas vezes tem o mesmo efeito que processar uma.
