-- Um registro por Pix liquidado: o crédito na chave de destino.
-- Ainda sem nenhuma garantia contra crédito em dobro.
create table pix_transfers (
    id            bigserial   primary key,
    transfer_id   bigint      not null,
    to_key        varchar(64) not null,
    amount_cents  bigint      not null,
    end_to_end_id varchar(64) not null,
    created_at    timestamptz not null default now()
);
