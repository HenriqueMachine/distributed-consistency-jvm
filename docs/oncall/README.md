# On-call: alerta, runbook e cookbook

Às 3h da manhã ninguém improvisa. Quem está de plantão recebe um **alerta**, abre o
**runbook** daquele alerta e segue as **receitas** do cookbook.

- O **runbook** diz *o quê*: o que o alerta significa, qual o impacto no cliente, onde olhar
  e quando escalar. Existe um por alerta.
- O **cookbook** diz *como*: receitas prontas e testadas, com os comandos exatos. Vários
  runbooks usam a mesma receita.

```
alerta ──► runbook (o quê) ──► receitas do cookbook (como) ──► conferir que voltou ao normal
```

## Alertas e runbooks

| Alerta | Métrica | Runbook |
|---|---|---|
| Mensagem nova numa DLT | `saga.dlt.messages` subiu | [dlt.md](runbooks/dlt.md) |
| Saga parada | `saga.state{state=NEEDS_ATTENTION}` > 0 | [saga-parada.md](runbooks/saga-parada.md) |
| SPI fora do ar | circuito `spi` em `OPEN` | [circuito-aberto.md](runbooks/circuito-aberto.md) |

As métricas saem em `/actuator/metrics` de cada serviço. Neste projeto não há Prometheus
nem Alertmanager: a métrica é o que dispararia o alerta, e o runbook começa a partir dela.

## Cookbook

[cookbook.md](cookbook.md): contar a história de uma transferência, conferir o dinheiro,
resgatar da DLT, ver o circuito e recriar o ambiente.

Na collection do Postman, a pasta **Cookbook (on-call)** tem as mesmas receitas, na ordem.

## Ensaio: um plantão do começo ao fim

Com o ambiente no ar (`docker compose up -d` e `./gradlew bootRun --parallel`):

1. Provoque o incidente: uma transferência com `X-Simulate: PIX_CRASH`.
   ```bash
   curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
     -H 'X-Simulate: PIX_CRASH' -d '{"from": "bia", "to": "henrique", "amount": 50.00}'
   ```
2. Em uns 10 s, o alerta: `saga.dlt.messages` sobe no pix-service. Abra o
   [runbook da DLT](runbooks/dlt.md).
3. Em uns 40 s, a saga desiste e vai para `NEEDS_ATTENTION`: é o segundo alerta, o mesmo
   incidente visto pelo lado do cliente.
4. Siga as receitas do cookbook: 1 (contar a história), 2 (conferir o dinheiro), 3 (investigar a
   mensagem morta) e 4 (resgatar).
5. A transferência termina `COMPLETED`, e o resgate fica registrado com quem, quando e por quê.
