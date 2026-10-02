# Passo 7 · Mortician

> Slides 38–39 · `git checkout passo-7`

## A ideia

> Toda DLT tem dono, alerta e runbook. Sem isso, é lixeira.

O **Mortician** é um serviço que consome **todas** as DLTs, guarda cada mensagem com o erro
e expõe três operações: listar, investigar e republicar. A DLT deixa de ser um tópico que
ninguém olha e vira uma fila de triagem com dono.

Republicar também é escrever em dois lugares (o registro do resgate e o tópico). Por isso
o Mortician **republica pela outbox**: senão seria escrita dupla de novo (passo 2).

## O que mudou no código

| Onde | O quê |
|---|---|
| `mortician-service/.../infra/messaging/DeadLetterListener.kt` | `@KafkaListener(topicPattern = ".*\\.DLT")`: lê os headers `kafka_dlt-*` e guarda |
| `mortician-service/.../domain/DeadLetter.kt` | `republish(by, reason, at)`: regra pura, resgate só uma vez e sempre com dono e motivo |
| `mortician-service/.../application/DeadLetterService.kt` | Republica no tópico original **pela outbox**, com um `messageId` novo e **sem** o header `simulate`; o resgate é marcado só se a mensagem ainda estiver `NEW` |
| `mortician-service/.../infra/config/MorticianConfig.kt` | O Mortician não manda nada para a DLT: retenta até conseguir guardar |
| `mortician-service/.../V1__dead_letters.sql` | Único por `(dlt_topic, dlt_partition, dlt_offset)`: entregas repetidas não duplicam |
| `shared-messaging/.../outbox/OutboxRepository.kt` | `save(OutboxRecord)`: grava uma mensagem pronta, sem passar pelo codec |
| `transfer-service/.../SagaStateMachine.kt` | `NEEDS_ATTENTION` + `PixSettled` → `COMPLETED` (e `DebitRefunded` → `CANCELLED`): o resgate conclui a saga |
| `compose.yaml` | `mortician-db` (5436) e, no perfil `apps`, o `mortician-service` (8084) |

Três decisões que valem um minuto de explicação:

- **Por que o Mortician não usa o handler com DLT?** Se ele falhasse ao guardar, criaria um
  `pix.commands.DLT.DLT`, que o próprio padrão `.*\.DLT` voltaria a consumir.
- **Por que um `messageId` novo?** Se a correção não funcionar, a mensagem morre de novo e
  volta para a DLT; com o id antigo, o segundo resgate colidiria na outbox. Repetir o
  comando é seguro porque o destino é idempotente pela chave de negócio (passo 4).
- **Por que sem `simulate`?** Na apresentação, o `PIX_CRASH` faz o papel do bug. Tirar o
  header é o "deploy da correção" antes do resgate.

## Rode

```bash
docker compose down && rm -rf logs && docker compose up -d
./gradlew bootRun --parallel          # agora são 4 serviços
```

Quebre o Pix e espere a saga desistir (uns 40 s):

```bash
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -H 'X-Simulate: PIX_CRASH' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'

curl -s localhost:8081/transfers/1042                     # "state":"NEEDS_ATTENTION"
curl -s 'localhost:8084/dead-letters?transferId=1042'     # 3 mensagens NEW
```

```
[mortician] 1042 dead letter 1 guardada: SendPix pix.commands → pix.commands.DLT: PixProcessingException: falha ao processar o Pix (simulada) …
[mortician] 1042 dead letter 2 guardada: SendPix pix.commands → pix.commands.DLT: …
[mortician] 1042 dead letter 3 guardada: SendPix pix.commands → pix.commands.DLT: …
[transfer]  1042 PIX_PENDING → NEEDS_ATTENTION cmd=- (timeout 12s sem resposta após 3 tentativas)
```

Investigue e resgate:

```bash
curl -s localhost:8084/dead-letters/1

curl -s -X POST localhost:8084/dead-letters/1/republish -H 'Content-Type: application/json' \
  -d '{"reason": "bug do PIX_CRASH corrigido no deploy", "requestedBy": "henrique"}'
```

```
[mortician] 1042 dead letter 1 republicada em pix.commands por henrique: bug do PIX_CRASH corrigido no deploy
[pix]       1042 Pix liquidado R$ 150,00 para henrique endToEndId=E1042… → PixSettled
[transfer]  1042 NEEDS_ATTENTION → COMPLETED cmd=- (Pix liquidado depois de NEEDS_ATTENTION endToEndId=E1042…)
```

É o `REPUBLISHED → PixSettled → COMPLETED` do laboratório (slide 49). Republicar a mesma
mensagem de novo devolve `409`, e as outras duas mensagens mortas, se republicadas, caem na
idempotência do pix-service.

## Quebre

O sistema agora se recupera, mas tente responder **"cadê a transação 1042?"** só com os
logs:

```bash
grep 'transferId=1042' logs/*.log      # nada
grep 1042 logs/*.log                   # várias linhas, mas não é a história toda
```

O que falta:

```
[pix]      o.s.k.l.KafkaMessageListenerContainer : Record in retry and not yet recovered   ← de qual transferência?
[transfer] OutboxRelay : relay simulate=DUPLICATE: SendPix messageId=31a0…                  ← de qual transferência?
```

- as linhas do Spring Kafka e do relay não sabem de qual transferência estão falando;
- cada serviço escreve o id de um jeito (`1042 …`, `transferência 1042 …`), e `grep 1042`
  também pegaria um offset 1042 ou um valor de R$ 1.042;
- não dá para separar a tentativa 1 do débito da tentativa 2, nem saber qual comando gerou
  qual resposta.

## Por que o próximo passo existe

> Aceite: "o que aconteceu com a 1042?" se responde com um grep.

No [passo 8](passo-8.md), um **correlation id em árvore** passa a viajar no header de toda
mensagem e entra no MDC de toda thread que trabalha numa transferência. Além disso, toda
transição vira um log e uma métrica.
