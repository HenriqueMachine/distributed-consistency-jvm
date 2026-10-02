# Runbook: saga parada

**Alerta:** `saga.state{state=NEEDS_ATTENTION}` maior que zero.

## O que significa

A saga mandou o mesmo comando 3 vezes, com a mesma chave, e não teve resposta. Ela desistiu
**sem compensar**: sem resposta, ela não sabe se o passo aconteceu, e estornar no escuro
pode devolver dinheiro que nem saiu.

`DEBIT_UNKNOWN` por muito tempo é o mesmo problema, um pouco antes: a saga ainda está
tentando.

## Impacto no cliente

A transferência fica "em análise". O dinheiro pode estar em qualquer ponto do caminho: só a
[receita 2](../cookbook.md#2-conferir-o-dinheiro) diz onde.

## Onde olhar, nesta ordem

1. [Receita 1](../cookbook.md#1-contar-a-história-de-uma-transferência). A transição antes de
   `NEEDS_ATTENTION` diz qual passo ficou sem resposta:

   | Veio de | Ficou sem resposta |
   |---|---|
   | `DEBIT_UNKNOWN` | o débito |
   | `PIX_PENDING` | o Pix |
   | `REFUNDING` | o estorno |

2. [Receita 2](../cookbook.md#2-conferir-o-dinheiro): o que de fato aconteceu com o dinheiro.
3. [Receita 3](../cookbook.md#3-achar-e-investigar-a-mensagem-morta): existe mensagem morta
   dessa transferência?
4. Saúde do participante (`/actuator/health`) e lag do consumer group no Kafka UI.

## Como corrigir

- **Há mensagem morta:** siga o [runbook da DLT](dlt.md). Uma resposta de sucesso que chega
  depois (`PixSettled` ou `DebitRefunded`) tira a saga de `NEEDS_ATTENTION` sozinha.
- **O SPI estava fora:** se o circuito abriu (ver [circuito aberto](circuito-aberto.md)), o
  Pix sai quando o SPI voltar, e a saga termina `COMPLETED` sozinha.
- **Nenhum dos dois:** não existe botão para forçar o estado da saga, de propósito. É decisão
  humana: escale com a história (receita 1) e o dinheiro (receita 2) em mãos.

Limite conhecido deste projeto: se a saga desistiu em `DEBIT_UNKNOWN` e o `AccountDebited`
chega depois, ele é ignorado. O débito existe (receita 2), mas a saga não anda. Também é
caso de escalar.

## Quando escalar

Sempre que não houver mensagem morta para resgatar, ou a receita 2 não fechar.

## Acabou quando

`saga.state{state=NEEDS_ATTENTION}` volta a zero, e cada transferência envolvida está
`COMPLETED` ou `CANCELLED`, com o dinheiro conferido.

No código: [`SagaStateMachine`](../../../transfer-service/src/main/kotlin/workshop/saga/transfer/domain/saga/SagaStateMachine.kt)
(`retryOrGiveUp` e o estado `NEEDS_ATTENTION`) e
[`SagaTimeoutScanner`](../../../transfer-service/src/main/kotlin/workshop/saga/transfer/infra/scheduling/SagaTimeoutScanner.kt).
