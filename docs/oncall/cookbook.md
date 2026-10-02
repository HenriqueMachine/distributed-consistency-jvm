# Cookbook do on-call

Receitas prontas para os [runbooks](README.md). Cada uma diz quando usar, os comandos e o
que esperar. Os exemplos usam a transferência `1042`; troque pelo id do seu incidente.

A pasta **Cookbook (on-call)** da collection do Postman tem as mesmas receitas, na ordem.

## 1. Contar a história de uma transferência

**Quando:** sempre primeiro. Antes de mexer em qualquer coisa, entenda o que aconteceu.

```bash
curl -s localhost:8081/transfers/1042                 # estado atual da saga
curl -s localhost:8081/transfers/1042/transitions     # cada transição: motivo, evento, cid, versão
grep -h "TRF-1042" logs/*.log | sort                  # a saga inteira, dos 4 serviços, em ordem
```

**O que esperar:** a última transição diz onde a saga parou e por quê. O correlation id
mostra o caminho: `TRF-1042.PIX-c3` é o comando de Pix; `TRF-1042.DEB-a1` e `TRF-1042.DEB-b7`
são duas tentativas do mesmo débito; `TRF-1042.PIX-c3.RPB-9d` é um resgate do Mortician.

## 2. Conferir o dinheiro

**Quando:** antes de qualquer correção. A pergunta do cliente é "cadê meu dinheiro?".

```bash
curl -s "localhost:8082/debits?transferId=1042"       # débitos e estornos da origem
curl -s "localhost:8083/pix?transferId=1042"          # créditos no destino
curl -s localhost:8082/participants                   # saldos atuais
```

**O que esperar:**

| Débitos | Estornos | Créditos | Situação |
|---|---|---|---|
| 1 | 0 | 1 | Concluída: o dinheiro chegou |
| 1 | 1 | 0 | Cancelada: o dinheiro voltou |
| 1 | 0 | 0 | **Em aberto**: saiu da origem e não chegou. É o caso do incidente |
| 0 | 0 | 0 | Nada aconteceu com o dinheiro |

Mais de um débito ou mais de um crédito para a mesma transferência nunca deveria aparecer:
a constraint `unique (transfer_id)` impede. Se aparecer, escale na hora.

## 3. Achar e investigar a mensagem morta

**Quando:** o alerta de DLT disparou, ou a saga parou e você quer saber se há algo para resgatar.

```bash
curl -s "localhost:8084/dead-letters?status=NEW"                  # tudo que ninguém tratou
curl -s "localhost:8084/dead-letters?status=NEW&transferId=1042"  # só desta transferência
curl -s localhost:8084/dead-letters/3                             # uma, com payload e erro
```

**O que esperar:** `originalTopic` diz de onde veio; `error` diz por que morreu; `payload` é a
mensagem original. No Kafka UI, os headers `kafka_dlt-*` da mensagem no tópico `*.DLT`
contam a mesma coisa.

**Decida antes de seguir:**

- `InvalidPayloadException` (payload inválido): **não republique**. O mesmo payload morre de
  novo. Escale para o time de quem produziu a mensagem.
- A causa foi corrigida (deploy, dependência de volta): siga para a receita 4.
- Não sabe a causa: **não republique**. Escale.

## 4. Resgatar da DLT

**Quando:** a causa foi entendida e corrigida (receita 3).

```bash
curl -s -X POST localhost:8084/dead-letters/3/republish -H 'Content-Type: application/json' \
  -d '{"reason": "bug do PIX_CRASH corrigido no deploy", "requestedBy": "henrique"}'
```

**O que esperar:** a mensagem volta ao tópico original **pela outbox**, com um `messageId`
novo e o cid filho `…RPB-xx`. A dead letter fica `REPUBLISHED`, com quem, quando e por quê.
Repetir o resgate devolve `409`: o histórico não se sobrescreve. Sem `reason`, devolve `400`;
sem `requestedBy`, o resgate fica registrado como `apresentador`.

Uma transferência pode ter **várias** mensagens mortas: cada reenvio da saga (até 3) pode
morrer de novo. Basta resgatar uma, a mais recente. As outras continuam `NEW`; republicá-las
é seguro (o destino é idempotente e responde "já liquidado"), mas não é necessário.

**Confira:** receita 1 (a saga termina `COMPLETED` ou `CANCELLED`) e receita 2 (o dinheiro
fecha a conta).

## 5. Ver o SPI e o circuito

**Quando:** o alerta de circuito aberto, ou muitas transferências paradas em `PIX_PENDING`.

```bash
curl -s localhost:8083/spi                            # SPI fora até quando, e estado do circuito
curl -s localhost:8083/actuator/circuitbreakers       # detalhes do circuito spi
grep -h "circuito spi" logs/pix-service.log           # quando abriu e quando fechou
```

**O que esperar:** `OPEN` = o consumidor `pix` está pausado e as mensagens esperam no
tópico; no Kafka UI, o lag do consumer group `pix-service` cresce. `HALF_OPEN` = uma chamada
de teste vai passar. `CLOSED` = normal.

Só na demonstração: `curl -s -X DELETE localhost:8083/spi/outage` traz o SPI de volta na hora.

## 6. Ver as métricas

**Quando:** para confirmar o alerta e para confirmar que o incidente acabou.

```bash
curl -s "localhost:8081/actuator/metrics/saga.state?tag=state:NEEDS_ATTENTION"
curl -s "localhost:8081/actuator/metrics/saga.state?tag=state:DEBIT_UNKNOWN"
curl -s localhost:8083/actuator/metrics/saga.dlt.messages
curl -s localhost:8082/actuator/metrics/saga.dlt.messages
```

**O que esperar:** `saga.state` é recalculado do banco a cada 5 s. `saga.dlt.messages` é um
contador: só sobe, e só existe depois da primeira mensagem morta do serviço (antes, `404`). O incidente acabou quando `NEEDS_ATTENTION` volta a zero e o contador da
DLT para de subir.

## 7. Recriar o ambiente (só na demonstração)

**Quando:** antes de apresentar, para a próxima transferência voltar a ser a 1042.

```bash
docker compose --profile apps down && rm -rf logs
docker compose up -d && ./gradlew bootRun --parallel
```

No IntelliJ: configuração **Recriar ambiente (down + logs + up)**.
