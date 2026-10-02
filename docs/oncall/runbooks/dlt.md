# Runbook: mensagem nova numa DLT

**Alerta:** `saga.dlt.messages{topic=…}` subiu em algum serviço.

## O que significa

Uma mensagem falhou 4 vezes (a primeira tentativa e mais 3 retries, em 1 s, 2 s e 4 s), ou
era inválida e foi direto. Ela saiu do caminho para a partição continuar andando: as
outras transferências não ficam presas atrás dela.

O [Mortician](../../../mortician-service) já guardou a mensagem com o erro. Nada se perdeu:
ela está esperando um dono.

## Impacto no cliente

A transferência daquela mensagem parou. Depende do tópico:

| Tópico | O cliente vê |
|---|---|
| `pix.commands.DLT` | A foi debitada e o B ainda não recebeu: "saiu da minha conta e não chegou" |
| `account.commands.DLT` | A transferência fica em processamento; o débito pode não ter acontecido |
| `*.replies.DLT` | O participante agiu, mas o orquestrador não soube: a saga fica esperando |

Depois de 3 reenvios sem resposta, a saga vai para `NEEDS_ATTENTION` (ver
[saga parada](saga-parada.md)). Não há estorno automático: timeout quer dizer "não sei".

## Onde olhar, nesta ordem

1. [Receita 3](../cookbook.md#3-achar-e-investigar-a-mensagem-morta): quais mensagens, de
   qual transferência, com qual erro.
2. [Receita 1](../cookbook.md#1-contar-a-história-de-uma-transferência): a história da transferência.
3. [Receita 2](../cookbook.md#2-conferir-o-dinheiro): onde está o dinheiro.

## Como corrigir

- **Payload inválido** (`InvalidPayloadException`): não republique. Escale para o time que
  produziu a mensagem.
- **Bug ou dependência, já corrigidos**: [receita 4](../cookbook.md#4-resgatar-da-dlt), com o
  motivo no `reason`.
- **Causa desconhecida**: não republique. Escale.

## Quando escalar

- Várias mensagens no mesmo tópico em poucos minutos: é um incidente do serviço, não uma
  mensagem isolada. Chame o time dono.
- Payload inválido: o time de quem produziu.
- Qualquer dúvida sobre o dinheiro (receita 2 não fecha): na hora.

## Acabou quando

- a saga está `COMPLETED` ou `CANCELLED` (receita 1);
- a dead letter está `REPUBLISHED`, com quem e por quê;
- `saga.dlt.messages` parou de subir (receita 6).

No código: [`KafkaErrorHandlingConfig`](../../../shared-messaging/src/main/kotlin/workshop/saga/messaging/errors/KafkaErrorHandlingConfig.kt)
manda para a DLT; [`DeadLetterService`](../../../mortician-service/src/main/kotlin/workshop/saga/mortician/application/DeadLetterService.kt)
guarda e republica.
