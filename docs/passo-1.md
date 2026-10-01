# Passo 1 · Esqueleto

> Slides 19–20 · `git checkout passo-1`

## A ideia

Uma transferência, três bancos de dados, uma promessa: ou o dinheiro sai da Ana e chega ao
Henrique, ou tudo volta como estava. Antes de qualquer saga, montamos o terreno: três serviços,
cada um com o seu Postgres, e um Kafka no meio.

## O que tem neste passo

| Onde | O quê |
|---|---|
| `compose.yaml` | Kafka 4.3 em KRaft (sem ZooKeeper), Kafka UI e 3 Postgres. Os serviços ficam no perfil `apps` |
| `buildSrc/` | Convenções de build compartilhadas; `bootRun` roda a partir da raiz |
| `*/application.yml` | Cada serviço loga no console **e** em `logs/<serviço>.log` |
| `*/infra/messaging/KafkaTopicsConfig.kt` | Cada serviço declara os tópicos que **consome** (3 partições cada) |
| `transfer-service` | `POST /transfers` grava a transferência e a saga em `CREATED`; `GET /transfers/{id}` |
| `account-service` | `POST/GET /participants`: o cadastro das pessoas da sala |
| `contracts/.../Money.kt` | Dinheiro em centavos, compartilhado por todos |
| `transfer-service/.../V1__transfers_and_sagas.sql` | A sequência de transferências começa em **1042** |
| `account-service/.../V1__accounts.sql` | Ana (R$ 1.000,00) e Henrique (R$ 500,00) |

A transferência (`transfers`) e a saga (`sagas`) são tabelas separadas de propósito: a
transferência é o que o cliente pediu; a saga é o andamento do pedido.

`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`: um tópico só existe se algum serviço o declarar.
Isso evita que um erro de digitação crie um tópico novo com uma partição só.

## Rode

```bash
docker compose up -d
./gradlew bootRun --parallel
```

Confira os serviços (`/actuator/health`), os tópicos vazios no Kafka UI
(http://localhost:8080) e os logs:

```bash
curl -s localhost:8081/actuator/health
tail -f logs/*.log
```

Cadastre alguém da sala e dispare a transferência:

```bash
curl -s -X POST localhost:8082/participants -H 'Content-Type: application/json' \
  -d '{"name": "Maria Souza", "pixKey": "maria", "balance": 1000.00}'

curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'
# {"id":1042,"from":"ana","to":"henrique","amount":150.00,"state":"CREATED"}
```

## Quebre

Não precisa fazer nada: espere e consulte de novo.

```bash
curl -s localhost:8081/transfers/1042
```

```
[transfer] transferência 1042 criada ana → henrique valor=R$ 150,00 → CREATED
```

A transferência fica em `CREATED` para sempre. Os tópicos estão vazios: os serviços ainda
não conversam.

## Por que o próximo passo existe

Precisamos de alguém que conduza a transferência de um serviço ao outro e saiba desfazer
o caminho se algo der errado. No [passo 2](passo-2.md), o transfer-service vira o
orquestrador da saga.
