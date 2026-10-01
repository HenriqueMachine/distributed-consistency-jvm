# Design · Transação #1042

Este documento registra as decisões de escopo e de arquitetura do repositório. É a
referência de quem mantém o código. Para acompanhar a apresentação, comece pelo
[README](../README.md).

## Objetivo

Este repositório é o código da palestra hands-on **"Transação #1042 · Falha não é exceção:
é parte do fluxo"**. O apresentador roda a demonstração na própria máquina, e clonar o
projeto é opcional para quem assiste. Quem clonar consegue:

1. subir o ambiente com dois comandos;
2. andar pelos 8 passos com `git checkout passo-N`;
3. reproduzir a falha ("quebra") de cada passo e entender pelos logs por que o passo
   seguinte existe;
4. no fim, resolver o laboratório só com `grep`.

Critério de aceite final: a pergunta *"o que aconteceu com a transação 1042?"* se responde
com um `grep transferId=1042` (ou `grep TRF-1042`), sem abrir o banco.

## Decisões

| Tema | Decisão |
|---|---|
| Linguagem e framework | Kotlin 2.3, Spring Boot 4.1, JDK 21 |
| Build | Gradle Kotlin DSL, monorepo multi-módulo, convenções em `buildSrc` |
| Persistência | `JdbcClient` + Flyway (SQL explícito, como nos slides) |
| Mensageria | Kafka 4.3 em KRaft, Spring Kafka, Kafka UI (kafbat) |
| Resiliência | Resilience4j 2.4 (`resilience4j-spring-boot4`), circuit breaker do SPI |
| Execução padrão | `docker compose up -d` sobe a infra; `./gradlew bootRun --parallel` sobe os serviços |
| Execução alternativa | `docker compose --profile apps up -d --build` sobe tudo no Docker (sem JDK) |
| Logs | Console + `logs/<serviço>.log` nos dois modos de execução |
| Passos | Histórico linear em `main`, uma tag `passo-N` por passo, código acumulativo |
| Conteúdo de cada tag | Estado final do passo, **incluindo** a quebra reproduzível que motiva o próximo |
| Disparo de falhas | Header HTTP `X-Simulate`, propagado como header Kafka `simulate`. Indisponibilidade do SPI por endpoint |
| DLT | Passo 6: visível. Passo 7: o Mortician guarda, lista e republica |
| Testes | Unitários do núcleo puro + E2E dos cenários contra o ambiente em execução |
| CI | GitHub Actions em push na `main` e em cada tag |
| Docs | README + `docs/passo-N.md` por passo + `docs/lab.md`, em PT-BR |
| Licença | MIT |

## Arquitetura

```
                              Kafka (KRaft)
transfer-service  ──────►  account.commands  ──────►  account-service
REST /transfers   ◄──────  account.replies   ◄──────  db: accounts · /participants
orquestrador      ──────►  pix.commands      ──────►  pix-service ──► SPI (simulado)
db: transfers     ◄──────  pix.replies       ◄──────  db: pix

                           *.DLT  ──────────────────►  mortician-service (passo 7)
                                                       db: mortician
```

| Serviço | Papel | Banco | Porta |
|---|---|---|---|
| transfer-service | `POST/GET /transfers` + orquestrador da saga | transfers (5433) | 8081 |
| account-service | cadastro de participantes, débito e estorno | accounts (5434) | 8082 |
| pix-service | envio ao SPI simulado e crédito no destino | pix (5435) | 8083 |
| mortician-service | dono das DLTs: guarda, lista e republica | mortician (5436) | 8084 |

- Orquestração (não coreografia): o orquestrador mora no `transfer-service`.
- A chave de toda mensagem é o `transferId`. Todos os tópicos têm 3 partições. As DLTs
  (`<tópico>.DLT`) são criadas com o mesmo número de partições, porque o
  `DeadLetterPublishingRecoverer` publica na partição de origem.
- Cada serviço declara (`NewTopic`) os tópicos que consome e as DLTs deles.
- Os ids de transferência vêm de uma sequência que começa em **1042**: a primeira
  transferência de um ambiente novo é a da história.

### Participantes

O `account-service` é o dono das contas.

```
POST /participants  {"name": "Maria Souza", "pixKey": "maria", "balance": 1000.00}
GET  /participants  → contas com saldo
```

- Quem **envia** precisa ter conta: o débito sai dela.
- Quem **recebe** é identificado pela chave Pix. O `pix-service` representa o "outro
  banco": aceita qualquer chave e registra o crédito, exceto `conta-encerrada`, que é
  recusada (`PixRejected`) e dispara a compensação.
- Ana (R$ 1.000,00) e Henrique (R$ 500,00) vêm no seed.

### Módulos

```
buildSrc/            convenções de build (kotlin, spring-service, spring-library)
contracts/           mensagens (sealed interfaces), headers, Simulation, codec JSON   (passo 2+)
shared-messaging/    publicação/leitura, outbox + relay, inbox, error handler         (passo 2+)
transfer-service/    REST + orquestrador da saga
account-service/     participantes, débito e estorno
pix-service/         SPI simulado, crédito e circuit breaker
mortician-service/   consumidor das DLTs                                              (passo 7+)
e2e-tests/           cenários ponta a ponta (`./gradlew e2e`)
```

### Organização de cada serviço

Núcleo funcional, casca imperativa:

```
domain/        regras puras (sem Spring, sem I/O): decide(), debit(), sendPix()
application/   casos de uso: carregam estado, chamam o domínio, gravam, emitem mensagens
infra/web            controllers
infra/messaging      listeners (traduzem mensagem → evento de domínio)
infra/persistence    repositórios JdbcClient
```

O coração é `SagaStateMachine.decide(transfer, saga, event, now): Decision`, uma função
pura que devolve o novo estado, os comandos a emitir e o motivo da transição (slide 21).

### Máquina de estados (versão final)

```
CREATED → DEBIT_PENDING → PIX_PENDING → COMPLETED
DEBIT_PENDING  --timeout-->  DEBIT_UNKNOWN  --reenvio mesma chave-->  (debitado) PIX_PENDING
DEBIT_PENDING|UNKNOWN --DebitDeclined--> CANCELLED
PIX_PENDING --PixRejected--> REFUNDING --DebitRefunded--> CANCELLED
qualquer pendente --tentativas esgotadas--> NEEDS_ATTENTION
NEEDS_ATTENTION --PixSettled (ex.: resgate pelo Mortician)--> COMPLETED
NEEDS_ATTENTION --DebitRefunded--> CANCELLED
```

Respostas que chegam fora de hora (estado já avançou) são ignoradas e logadas.

### Log de transições (slide 23)

A tabela `sagas` guarda o estado operacional (estado atual, prazo, tentativas). Ao lado
dela, `saga_transitions` é **só de inserção**, com `revoke update, delete`. Cada
transição grava `from_status`, `to_status`, `reason`, `event_id`, `cid` e `app_version`
(o git sha do serviço, gerado no build). A tabela responde "por que a 1042 foi estornada?".

### Simulações

| Gatilho | Quem reage | Efeito | Passo em que vira quebra |
|---|---|---|---|
| `X-Simulate: CRASH_AFTER_SEND` | transfer-service | lança exceção depois do `send`, antes do commit | passo 2 (escrita dupla) |
| `X-Simulate: DUPLICATE` | relay da outbox | publica a mesma linha duas vezes (queda antes de marcar) | passo 3 |
| `X-Simulate: DEBIT_SLOW` | account-service | debita na hora, mas a resposta só sai 15 s depois | passo 4 |
| `X-Simulate: PIX_CRASH` | pix-service | o consumidor lança exceção em toda tentativa | passo 5 |
| `to: conta-encerrada` | pix-service | `PixRejected` → compensação (regra de negócio, não falha) | — |
| `POST /spi/outage?seconds=15` | pix-service | SPI fora do ar para todos: abre o circuit breaker | passo 6 |

As simulações ficam fora das regras de negócio: um `FailureSimulator` por serviço e, para
`DUPLICATE`, um único ponto no `OutboxRelay`.

## Os 8 passos

| Tag | Entrega | Quebra reproduzível |
|---|---|---|
| passo-1 | Monorepo, compose (infra + perfil `apps`), tópicos, participantes, `POST/GET /transfers` grava a transferência e a saga em CREATED | A transferência não sai do lugar |
| passo-2 | Contratos, `decide()` puro, débito, Pix, compensação por estorno, `saga_transitions`. `send` direto no `@Transactional`. Timeout ingênuo do débito: compensa | `CRASH_AFTER_SEND`: débito de uma transferência que não existe |
| passo-3 | Outbox + relay `@Scheduled` em todos os serviços | `DUPLICATE`: Ana debitada duas vezes |
| passo-4 | Idempotência: `processed_messages` + `unique(transfer_id)` em `debits` e `pix_transfers`, um estorno por débito | `DEBIT_SLOW`: o timeout dispara o estorno de um débito que deu certo |
| passo-5 | `DEBIT_UNKNOWN`, reenvio com a mesma chave, limite → `NEEDS_ATTENTION`, compensação só com "não" explícito | `PIX_CRASH`: o handler padrão descarta a mensagem e a saga fica parada em PIX_PENDING |
| passo-6 | `DefaultErrorHandler` 1/2/4 s → DLT, `InvalidPayloadException` sem retry, prazo em todos os estados pendentes, circuit breaker do SPI (circuito aberto pausa o consumidor) | A DLT vira lixeira: só se olha com `kafka-console-consumer` |
| passo-7 | Mortician: `dead_letters`, `GET /dead-letters`, `POST /dead-letters/{id}/republish` pela outbox (sem `simulate`), `NEEDS_ATTENTION` + `PixSettled` → `COMPLETED` | "Cadê a transação 1042?": logs sem correlação |
| passo-8 | Correlation id em árvore (`x-cid`), MDC com `transferId` e `cid`, log de toda transição, métricas, perfil `json` com ECS | Pronto: o laboratório se resolve com grep |

A cada passo, o E2E do passo traz um teste que **documenta a quebra**, e o passo seguinte
inverte a asserção.

## Circuit breaker (passo 6, slide 35)

- O `pix-service` chama o `SpiGateway` através do circuit breaker `spi` (Resilience4j,
  configurado em `resilience4j.circuitbreaker.instances.spi.*` no `application.yml`).
- `SPI indisponível` é erro transitório e **não vai para a DLT**: o error handler retenta
  em intervalo fixo enquanto o circuito decide.
- Na transição para OPEN, o container do listener `pix` é pausado; o lag cresce no Kafka
  UI. Em HALF_OPEN, ele é retomado para testar; se fechar, a fila escoa.
- O prazo do passo Pix da saga cabe a indisponibilidade padrão da demonstração.

## Mortician (passo 7, slide 37)

- `@KafkaListener(topicPattern = ".*\\.DLT")`, grupo próprio. Grava em `dead_letters`:
  tópico original, chave, payload, headers, exceção, `transferId`, `cid` e status
  (`NEW`, `REPUBLISHED`).
- `GET /dead-letters?status=&transferId=`, `GET /dead-letters/{id}`,
  `POST /dead-letters/{id}/republish {"reason": "...", "requestedBy": "..."}`.
- A republicação passa pela **outbox** do Mortician (senão vira escrita dupla de novo),
  vai para o tópico original com um `messageId` novo (a idempotência de negócio do destino
  cobre a repetição), e **sem** o header `simulate`: é a "correção do bug". Cada resgate
  registra quem, quando e por quê, e só acontece uma vez por mensagem morta.

## Observabilidade (passo 8, slides 40–41)

- Correlation id em árvore no header `x-cid`. A raiz é `TRF-1042`. O orquestrador cria
  um filho por comando (`TRF-1042.DEB-a1`, `TRF-1042.DEB-b7` no reenvio,
  `TRF-1042.PIX-c3`). Os participantes respondem com o cid do comando que receberam. O
  pix-service cria `….SPI-3f` para a chamada ao SPI, e o Mortician cria `….RPB-xx` ao
  republicar.
- O MDC tem `transferId` e `cid`. O console prefixa `transferId=1042 cid=TRF-1042.DEB-a1`
  em toda linha com contexto, inclusive as do Spring Kafka.
- Perfil `json`: `logging.structured.format.console=ecs`.
- Métricas em `/actuator/metrics`: `saga.transitions`, `saga.state` e
  `saga.dlt.messages`. O estado do circuito fica em `/actuator/circuitbreakers`. O lag
  aparece no Kafka UI.

## Padrões de código

- Usar os nomes do domínio dos slides (`DebitAccount`, `PixRejected`, `DEBIT_UNKNOWN`).
- Contratos como `sealed interface`, para que todo `when` seja exaustivo.
- Uma responsabilidade por classe. O domínio não conhece o Spring. Nada de interface
  com uma implementação só.
- Imutabilidade (`val`, data classes), funções curtas, sem `!!`.
- KDoc em todo tipo e função pública, explicando o papel na saga. Os comentários
  explicam o porquê e citam o passo e o slide. Defeitos intencionais ficam marcados com
  `⚠ QUEBRA passo-N` (encontre com `git grep QUEBRA`).

## Fora de escopo

- Coreografia, CDC/Debezium e transações Kafka (citados nos slides, não implementados).
- `limits-service` e `ledger-service` (slide 7): ilustração do conceito, não fazem parte
  da arquitetura.
- Diretório de chaves sincronizado entre serviços, cadastro em lote e autenticação.
- Prometheus/Grafana e front-end.
