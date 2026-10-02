# Runbook: SPI fora do ar (circuito aberto)

**Alerta:** circuito `spi` em `OPEN` (`/actuator/circuitbreakers` do pix-service), ou no log:
`circuito spi CLOSED → OPEN: pausando o consumidor pix`.

## O que significa

Pelo menos metade das últimas chamadas ao SPI falhou (janela de 20 chamadas, mínimo de 5).
O pix-service parou de chamar o SPI e **pausou o consumidor** de `pix.commands`: as
mensagens esperam no tópico em vez de falhar uma a uma. Nada vai para a DLT: dependência
fora do ar não é culpa da mensagem.

## Impacto no cliente

Todos os Pix atrasam. Nenhum se perde e nenhum duplica: o débito já foi feito, e o Pix sai
quando o SPI voltar.

Se a queda passar de uns 36 s (3 prazos de 12 s), as sagas em `PIX_PENDING` vão para
`NEEDS_ATTENTION`. Elas voltam sozinhas para `COMPLETED` quando o Pix liquidar.

## Onde olhar

1. [Receita 5](../cookbook.md#5-ver-o-spi-e-o-circuito): estado do SPI e do circuito.
2. Kafka UI → **Consumers** → `pix-service`: o lag crescendo é o trabalho represado.
3. [Receita 6](../cookbook.md#6-ver-as-métricas): quantas sagas estão paradas.

## Como corrigir

No nosso serviço, nada. O circuito testa sozinho a cada 10 s (`HALF_OPEN`): quando uma
chamada passa, ele fecha, o consumidor volta e o lag escoa.

O problema é do parceiro: acompanhe o status dele. Na demonstração, a receita 5 traz o SPI
de volta na hora.

## Quando escalar

- O circuito continua aberto por mais tempo do que o negócio aceita (defina esse tempo com
  o produto): comunicação com os clientes.
- O circuito fecha e abre sem parar: o parceiro está instável. Escale para quem fala com ele.

## Acabou quando

O circuito está `CLOSED`, o lag do `pix-service` voltou a zero, e não sobrou saga em
`NEEDS_ATTENTION` (senão, siga o [runbook da saga parada](saga-parada.md)).

No código: [`SpiGateway`](../../../pix-service/src/main/kotlin/workshop/saga/pix/infra/spi/SpiGateway.kt)
e [`SpiCircuitBreakerListener`](../../../pix-service/src/main/kotlin/workshop/saga/pix/infra/spi/SpiCircuitBreakerListener.kt).
