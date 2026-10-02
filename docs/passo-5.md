# Passo 5 · Timeout = "não sei"

> Slides 34–35 · `git checkout passo-5`

## A ideia

> Timeout quer dizer "não sei". Não quer dizer "falhou".

Compensar depois de um timeout pode estornar um débito que deu certo, ou debitar de novo
quem já foi debitado. A saída é **perguntar de novo, com a mesma chave**. Como o
account-service é idempotente desde o passo 4, a pergunta repetida é segura: ele devolve o
resultado anterior.

Regras do orquestrador:

- timeout → estado `DEBIT_UNKNOWN`, **nunca** compensação automática;
- reenvio idempotente do mesmo comando (a chave é o `transferId`);
- limite de tentativas → `NEEDS_ATTENTION`;
- compensar só com um "não" explícito (`DebitDeclined`, `PixRejected`).

## O que mudou no código

| Onde | O quê |
|---|---|
| `transfer-service/.../domain/saga/SagaState.kt` | Novos estados `DEBIT_UNKNOWN` e `NEEDS_ATTENTION` |
| `transfer-service/.../domain/saga/SagaStateMachine.kt` | `onDebitTimeout`: reenvia ou desiste; o `⚠ QUEBRA passo-4` sumiu |
| `transfer-service/.../domain/saga/Saga.kt` + `V6__saga_attempts.sql` | Contador de tentativas |
| `application.yml` | `saga.timeouts.max-attempts: 3` |
| `SagaStateMachineTest` | Cada regra acima é um teste, sem container |
| `pix-service/.../FailureSimulator.kt` | Nova simulação `PIX_CRASH` |

```bash
git diff passo-4 passo-5 -- transfer-service/src/main/kotlin/workshop/saga/transfer/domain
```

## Rode

Recrie o ambiente: começar do zero deixa os ids dos exemplos iguais aos seus.

```bash
docker compose down && rm -rf logs && docker compose up -d
./gradlew bootRun --parallel
```

Repita a quebra do passo 4:

```bash
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -H 'X-Simulate: DEBIT_SLOW' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'
```

```
 0s   [transfer] 1042 CREATED → DEBIT_PENDING cmd=DebitAccount (transferência criada)
 0,1s [account]  1042 debitado R$ 150,00 de ana debitId=d-1 → AccountDebited
 0,1s [account]  1042 simulate=DEBIT_SLOW: resposta retida por 15s
 8s   [transfer] 1042 DEBIT_PENDING → DEBIT_UNKNOWN cmd=DebitAccount (timeout 8s → não sei se debitou, reenviando com a mesma chave tentativa=2)
 8s   [account]  1042 já debitado debitId=d-1 → devolvendo resultado anterior
 8,2s [transfer] 1042 DEBIT_UNKNOWN → PIX_PENDING cmd=SendPix (débito aprovado debitId=d-1)
 8,4s [pix]      1042 Pix liquidado R$ 150,00 para henrique endToEndId=E1042… → PixSettled
 8,6s [transfer] 1042 PIX_PENDING → COMPLETED cmd=- (Pix liquidado endToEndId=E1042…)
15s   [transfer] 1042 AccountDebited ignorado: saga já está em COMPLETED
```

O resultado foi um débito, nenhum estorno e a transferência concluída. É a linha do tempo
do slide 37.

## Quebre

Agora o Pix quebra, e de um jeito que não passa com o tempo:

```bash
curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
  -H 'X-Simulate: PIX_CRASH' \
  -d '{"from": "ana", "to": "henrique", "amount": 150.00}'

grep -E 'PIX_CRASH|exhausted' logs/pix-service.log
```

```
[pix] 1043 simulate=PIX_CRASH: falha ao processar o Pix      ← 10 vezes em ~5 s
[pix] Backoff FixedBackOffExecution[interval=0, currentAttempts=10, maxAttempts=9]
      exhausted for pix.commands-0@5
```

O tratamento de erro **padrão** do Spring Kafka tenta 10 vezes, sem esperar entre as
tentativas, loga um erro… e **commita o offset**. A mensagem sumiu, ninguém a guardou, e o
orquestrador não tem prazo para o Pix:

```bash
curl -s localhost:8081/transfers/1043    # "state":"PIX_PENDING" para sempre
```

A Ana foi debitada e o Henrique nunca recebe. E se a falha fosse passageira (um lock, o banco
reiniciando), 10 tentativas em 5 s gastariam todas as chances antes de ela passar.

## Por que o próximo passo existe

Precisamos separar o que é **passageiro** (vale tentar de novo, com calma) do que **nunca
vai passar** (tem que sair do caminho e ficar guardado em algum lugar). No
[passo 6](passo-6.md) entram retry com backoff exponencial, Dead Letter Topic e um circuit
breaker para quando o problema é o SPI inteiro.
