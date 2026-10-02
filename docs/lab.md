# Laboratório · reconstrua a história pelos logs

> Slide 45 · `git checkout passo-8` · 20 min · em duplas (ou acompanhando a tela)

**Regras:** só vale `grep TRF-…` nos logs. Proibido abrir o banco.

```bash
docker compose down && rm -rf logs && docker compose up -d
./gradlew bootRun --parallel
```

Dispare os cenários (ou use a [collection do Postman](../postman/transacao-1042.postman_collection.json)) e anote o `id` que cada
`POST` devolve:

```bash
transfer() {  # uso: transfer <para> [X-Simulate]
  curl -s -X POST localhost:8081/transfers -H 'Content-Type: application/json' \
    ${2:+-H "X-Simulate: $2"} \
    -d "{\"from\": \"ana\", \"to\": \"$1\", \"amount\": 150.00}"; echo
}

transfer henrique                        # 1. caminho feliz
transfer conta-encerrada              # 2. conta encerrada
transfer henrique DUPLICATE              # 3. duplicata
transfer henrique DEBIT_SLOW             # 4. resposta lenta
transfer henrique PIX_CRASH              # 5. Pix quebrado
```

Espere uns 40 segundos (o cenário 5 é o mais demorado). Depois, resgate o 5 pelo Mortician:

```bash
curl -s 'localhost:8084/dead-letters?transferId=<id do 5>'
curl -s -X POST localhost:8084/dead-letters/<id da dead letter>/republish \
  -H 'Content-Type: application/json' -d '{"reason": "bug corrigido", "requestedBy": "dupla 3"}'
```

E, para cada transferência:

```bash
grep -h TRF-1042 logs/*.log | sort
```

## O que achar

| # | Cenário | Como disparar | O que achar nos logs |
|---|---|---|---|
| 1 | Caminho feliz | `POST /transfers` | `CREATED → … → COMPLETED` |
| 2 | Conta encerrada | `to=conta-encerrada` | `REFUNDING → CANCELLED`, 1 estorno |
| 3 | Duplicata | `X-Simulate: DUPLICATE` | "já processado", 1 débito só |
| 4 | Resposta lenta | `X-Simulate: DEBIT_SLOW` | `DEBIT_UNKNOWN`, reenvio, sem estorno |
| 5 | Pix quebrado | `X-Simulate: PIX_CRASH` | 3 tentativas → `.DLT` → `NEEDS_ATTENTION` |
| 6 | Resgate da DLT | `POST /dead-letters/{id}/republish` | `REPUBLISHED` → `PixSettled` → `COMPLETED` |

Para cada um, responda:

1. Em que estado a saga terminou, e por quê?
2. Quantas vezes a Ana foi debitada? E estornada?
3. Qual serviço "viu" o problema primeiro? Qual cid ele estava processando?
4. Se fosse produção, alguém precisaria agir? Quem, e com que informação?

<details>
<summary>Gabarito</summary>

**1. Caminho feliz.** Três transições no transfer (`CREATED → DEBIT_PENDING → PIX_PENDING
→ COMPLETED`), um débito e um Pix. A árvore tem três ramos: a raiz `TRF-…`, um `DEB-xx` e
um `PIX-xx`, com o `SPI-xx` pendurado nele. Ninguém precisa agir.

**2. Conta encerrada.** O pix loga `conta destino encerrada chave=conta-encerrada →
PixRejected`. O transfer entra em `REFUNDING` com `cmd=RefundDebit`, o account loga
`estornado` (cid `REF-xx`) e a saga termina em `CANCELLED`. Há um débito **e** um estorno, e
as duas linhas ficam no extrato (`GET localhost:8082/debits?transferId=…`). Compensar não é
desfazer.

**3. Duplicata.** O relay loga `simulate=DUPLICATE: … publicado 2×`. Do outro lado, aparece
`evento xxxx… já processado — ignorado (simulate=DUPLICATE)` no account, no pix e no
transfer. O resultado é um débito só, e a saga conclui normalmente.

**4. Resposta lenta.** O account debita na hora e loga `resposta retida por 15s`. Aos 8 s, o
transfer loga `DEBIT_PENDING → DEBIT_UNKNOWN … não sei se debitou, reenviando com a mesma
chave tentativa=2`. O account responde `já debitado → devolvendo resultado anterior` com um
**cid diferente** (`DEB-yy`, a tentativa 2), e a saga segue até `COMPLETED`. Aos 15 s, a
resposta da tentativa 1 (`DEB-xx`) chega e é `ignorado: saga já está em COMPLETED`. Nenhum
estorno.

**5. Pix quebrado.** O pix falha 4 vezes (1 + 3 retries, em 1 s, 2 s e 4 s), loga
`Record in retry and not yet recovered` entre elas e, por fim,
`falha tentativa=3 → pix.commands.DLT`. O Mortician loga `dead letter N guardada`. Aos 12 s
o transfer reenvia (`tentativa=2`, um `PIX-yy` novo) e o ciclo se repete. Depois da
terceira tentativa, `PIX_PENDING → NEEDS_ATTENTION`. Ficam três dead letters e
`saga.state{state=NEEDS_ATTENTION}` ≥ 1. A Ana foi debitada e o Henrique não recebeu:
**alguém precisa agir**, e o Mortician tem o payload, o erro e o cid.

**6. Resgate.** O Mortician loga `dead letter N republicada em pix.commands por …` com o cid
`….PIX-xx.RPB-zz`. O pix liquida (`SPI liquidou`), e o transfer loga
`NEEDS_ATTENTION → COMPLETED (Pix liquidado depois de NEEDS_ATTENTION …)`. Republicar a mesma dead letter de
novo devolve `409`. As outras duas, se republicadas, caem na idempotência do pix
(`Pix já liquidado → devolvendo resultado anterior`).

</details>
