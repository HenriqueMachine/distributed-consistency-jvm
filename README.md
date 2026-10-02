# Transação #1042

Código da palestra hands-on **"Transação #1042 · Falha não é exceção: é parte do fluxo"**:
sagas resilientes com Kotlin, Spring Boot e Kafka.

Uma transferência Pix de R$ 150,00 da Ana para o Henrique atravessa três serviços. A gente
derruba o sistema de propósito, lê cada falha nos logs e mostra o padrão que a resolve: saga,
outbox, idempotência, timeout, retry + DLT, circuit breaker, Mortician e correlation id.

```
                              Kafka (KRaft)
transfer-service  ──────►  account.commands  ──────►  account-service
REST /transfers   ◄──────  account.replies   ◄──────  db: accounts · /participants
orquestrador      ──────►  pix.commands      ──────►  pix-service ──► SPI (simulado)
db: transfers     ◄──────  pix.replies       ◄──────  db: pix
```

> Na apresentação, quem roda é o apresentador. Clonar e rodar é opcional, para quem quiser
> repetir depois com calma.

## Como rodar

Pré-requisitos: **Docker** com Compose v2, e **JDK 21** (sem JDK, veja a alternativa abaixo).

```bash
git clone https://github.com/HenriqueMachine/distributed-consistency-jvm.git
cd distributed-consistency-jvm

docker compose up -d                 # Kafka, Kafka UI e um Postgres por serviço
./gradlew bootRun --parallel         # os serviços (deixe este terminal aberto)
```

Em outro terminal:

```bash
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'

tail -f logs/*.log                   # cada serviço loga também em logs/<serviço>.log
```

**Sem JDK?** Tudo no Docker:

```bash
docker compose --profile apps up -d --build --wait
```

Para começar do zero (antes de apresentar, por exemplo), recrie o ambiente. A primeira
transferência volta a ser a 1042, então apague também os logs: senão `grep TRF-1042`
mistura histórias.

```bash
docker compose --profile apps down     # derruba infra (e serviços, se estiverem no Docker)
rm -rf logs                            # no Linux, se os serviços rodaram no Docker: sudo rm -rf logs
docker compose up -d && ./gradlew bootRun --parallel
```

| Endereço | O quê |
|---|---|
| http://localhost:8080 | Kafka UI: tópicos, mensagens, headers, consumer groups e lag |
| http://localhost:8081 | transfer-service (`POST /transfers`, `GET /transfers/{id}`) |
| http://localhost:8082 | account-service (`POST/GET /participants`) |
| http://localhost:8083 | pix-service (`GET /spi`, `POST /spi/outage`) |
| http://localhost:8084 | mortician-service (`GET /dead-letters`, `POST /dead-letters/{id}/republish`) |

## Mapa: falha → padrão → código

O que pode dar errado com o Pix da Ana, o padrão que resolve e onde ele está no código.

| O que pode dar errado | Padrão | Onde olhar |
|---|---|---|
| Três serviços precisam agir juntos | Saga orquestrada | [`SagaStateMachine.decide()`](transfer-service/src/main/kotlin/workshop/saga/transfer/domain/saga/SagaStateMachine.kt) |
| Um passo falha no meio | Compensação | [`DebitService.refund()`](account-service/src/main/kotlin/workshop/saga/account/application/DebitService.kt) |
| O servidor cai entre o banco e o Kafka | Outbox | [`MessagePublisher`](shared-messaging/src/main/kotlin/workshop/saga/messaging/MessagePublisher.kt) · [`OutboxRelay`](shared-messaging/src/main/kotlin/workshop/saga/messaging/outbox/OutboxRelay.kt) |
| A mensagem chega duas vezes | Idempotência | [`Inbox`](shared-messaging/src/main/kotlin/workshop/saga/messaging/inbox/Inbox.kt) · [`unique (transfer_id)`](account-service/src/main/resources/db/migration/V6__one_debit_per_transfer.sql) |
| Ninguém responde | Timeout = "não sei" | [`SagaTimeoutScanner`](transfer-service/src/main/kotlin/workshop/saga/transfer/infra/scheduling/SagaTimeoutScanner.kt) · `DEBIT_UNKNOWN` |
| A mensagem falha sempre | Retry + DLT | [`KafkaErrorHandlingConfig`](shared-messaging/src/main/kotlin/workshop/saga/messaging/errors/KafkaErrorHandlingConfig.kt) |
| O parceiro cai para todo mundo | Circuit breaker | [`SpiGateway`](pix-service/src/main/kotlin/workshop/saga/pix/infra/spi/SpiGateway.kt) · [`SpiCircuitBreakerListener`](pix-service/src/main/kotlin/workshop/saga/pix/infra/spi/SpiCircuitBreakerListener.kt) |
| A DLT enche e ninguém olha | Mortician | [`DeadLetterService.republish()`](mortician-service/src/main/kotlin/workshop/saga/mortician/application/DeadLetterService.kt) |
| "Cadê o dinheiro da Ana?" | Correlation id e logs | [`Cid`](contracts/src/main/kotlin/workshop/saga/contracts/Cid.kt) · [`SagaContext`](shared-messaging/src/main/kotlin/workshop/saga/messaging/observability/SagaContext.kt) |

Tudo que tem `Simulation` (`FailureSimulator`, header `X-Simulate`, `SpiOutage`, `GET /pix`,
`GET /debits`) é andaime da apresentação: existe só para provocar e provar falhas ao vivo.

## On-call: runbooks e cookbook

Quando o incidente acontece, o alerta aponta um **runbook** (o quê: impacto, onde olhar,
quando escalar), e o runbook aponta **receitas** do cookbook (como: os comandos exatos).

- [docs/oncall](docs/oncall/README.md): alertas, runbooks e um ensaio de plantão do começo ao fim.
- [Cookbook](docs/oncall/cookbook.md): contar a história de uma transferência, conferir o
  dinheiro, resgatar da DLT, ver o circuito.
- Runbooks: [mensagem na DLT](docs/oncall/runbooks/dlt.md),
  [saga parada](docs/oncall/runbooks/saga-parada.md),
  [SPI fora do ar](docs/oncall/runbooks/circuito-aberto.md).

### Collection do Postman

Importe [`postman/transacao-1042.postman_collection.json`](postman/transacao-1042.postman_collection.json)
no Postman (*Import* → arraste o arquivo). Insomnia e Bruno também importam esse formato.

- As pastas seguem os padrões da apresentação, e cada requisição explica o que esperar.
- A pasta **Cookbook (on-call)** tem as receitas do [cookbook](docs/oncall/cookbook.md), na ordem.
- Todo `POST /transfers` guarda o id criado na variável `{{transferId}}`. As requisições
  da pasta **Consultas** (estado, transições, extrato, Pix) usam essa variável, então não
  é preciso copiar o id.
- A listagem de mensagens mortas guarda o id da mais recente em `{{deadLetterId}}`, que o
  **Resgatar** usa.
- Para transferir a partir de alguém da sala: cadastre a pessoa em **0. Participantes** e
  troque a variável `{{from}}` da collection (*Variables*) pela chave dela.

### No IntelliJ IDEA

O repositório traz configurações de execução prontas na pasta `.run/`. Elas aparecem
sozinhas no seletor de execução (canto superior direito) quando você abre o projeto:

| Configuração | O que faz |
|---|---|
| **1. Infra (docker compose up)** | Sobe Kafka, Kafka UI e os Postgres, e espera ficarem saudáveis |
| **2. Todos os serviços (bootRun)** | Roda a infra antes e depois `./gradlew bootRun --parallel` |
| **3. Testes ponta a ponta (e2e)** | `./gradlew e2e`, com o ambiente no ar |
| **Recriar ambiente (down + logs + up)** | Começa do zero: a próxima transferência volta a ser a 1042 |
| **transfer-service**, **account-service**, **pix-service**… | Um serviço só, para rodar ou depurar (*Debug*) com breakpoints |

Para depurar um serviço: rode a **1. Infra**, depois os outros serviços com a
**2. Todos os serviços** ou um a um, e use *Debug* no que você quer inspecionar.

### Kafka UI: ver as mensagens

O Kafka UI roda em **http://localhost:8080** (sobe junto com a infra). Ele mostra as
**mensagens** que os serviços trocam. Os **logs** de cada serviço ficam em `logs/*.log`.

- **Topics** → um tópico (ex.: `account.commands`) → aba **Messages**: cada mensagem com a
  chave (`transferId`), o payload JSON e os **headers**: `messageId`, `messageType`,
  `simulate` e `x-cid`. Clique numa mensagem para ver tudo.
- Para achar uma transferência: em **Messages**, filtre por **Key** = `1042`.
- **Consumers** → um consumer group (ex.: `pix-service`): partições, offset commitado e
  **lag**. Derrube o SPI e veja o lag do `pix-service` crescer enquanto o circuito está
  aberto.
- Os tópicos `*.DLT` guardam as mensagens que morreram. Os headers
  `kafka_dlt-*` contam de onde elas vieram e por quê.
- **Produce Message** (dentro de um tópico) publica uma mensagem na mão. É útil para
  injetar um payload inválido e ver a DLT funcionar.

### Participantes da sala

Ana e Henrique vêm no cadastro inicial. Para a transferência acontecer entre pessoas da
plateia:

```bash
curl -s -X POST localhost:8082/participants -H 'Content-Type: application/json' \
  -d '{"name": "Maria Souza", "pixKey": "maria", "balance": 1000.00}'

curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -d '{"from": "maria", "to": "joao", "amount": 42.00}'

curl -s localhost:8082/participants  # saldos antes e depois
```

Quem envia precisa estar cadastrado. Quem recebe é só uma chave Pix: o pix-service faz o
papel do "outro banco" e aceita qualquer chave, menos `conta-encerrada`.

## Estude passo a passo (opcional)

Na apresentação roda o projeto completo, na `main`. Para estudar com calma, o código também
foi construído em 8 passos, um por tag: em cada tag o sistema funciona **e** tem um defeito
reproduzível, que motiva o passo seguinte.

| Tag | Passo | Guia | Quebra que motiva o próximo |
|---|---|---|---|
| `passo-1` | Esqueleto | [docs/passo-1.md](docs/passo-1.md) | Ninguém conversa: a transação não sai do lugar |
| `passo-2` | Saga orquestrada | [docs/passo-2.md](docs/passo-2.md) | Escrita dupla: banco e Kafka podem divergir |
| `passo-3` | Outbox | [docs/passo-3.md](docs/passo-3.md) | As mensagens chegam duplicadas |
| `passo-4` | Idempotência | [docs/passo-4.md](docs/passo-4.md) | Um timeout parece erro e dispara estorno |
| `passo-5` | Timeout = "não sei" | [docs/passo-5.md](docs/passo-5.md) | Algumas mensagens falham sempre |
| `passo-6` | Retry + DLT | [docs/passo-6.md](docs/passo-6.md) | A DLT vira lixeira: ninguém olha |
| `passo-7` | Mortician | [docs/passo-7.md](docs/passo-7.md) | "Cadê a transação 1042?" |
| `passo-8` | Observabilidade | [docs/passo-8.md](docs/passo-8.md) | Pronto para produção |

> Cada guia nasce na tag do seu passo. Navegando por uma tag antiga no GitHub, os links
> dos passos seguintes ainda não existem: veja a `main`.

Para voltar a um passo: `git checkout passo-3`, e recrie o ambiente (veja **Como rodar**).

Depois do passo 8: [laboratório final](docs/lab.md). Reconstrua a história de cada
transação só pelos logs.

Para ver exatamente o que um passo mudou:

```bash
git diff passo-2 passo-3 -- '*.kt' '*.sql'
```

Nas tags, os defeitos intencionais estão marcados no código com `⚠ QUEBRA passo-N`. Para
achá-los: `git grep QUEBRA` (na `main` não há nenhum).

## Estrutura do código

```
contracts/           tipos e mensagens compartilhados pelos serviços
shared-messaging/    outbox, idempotência e tratamento de erro do Kafka
transfer-service/    REST + orquestrador da saga
account-service/     participantes, débito e estorno
pix-service/         SPI simulado e crédito no destino
mortician-service/   dono das DLTs
e2e-tests/           cenários ponta a ponta
```

Cada serviço segue a mesma organização: `domain/` tem as regras puras, sem Spring e sem
I/O; `application/` tem os casos de uso; `infra/` tem web, mensageria e persistência. As
decisões de design estão em [docs/design.md](docs/design.md).

## Testes

```bash
./gradlew build      # compila e roda os testes unitários
./gradlew e2e        # cenários ponta a ponta, com o ambiente no ar (bootRun ou perfil apps)
```

Nas tags, os testes E2E incluem um teste que **documenta a quebra** do passo. No passo
seguinte, a asserção se inverte.

## Licença

[MIT](LICENSE)
