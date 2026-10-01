-- Um débito por linha. Ainda sem nenhuma garantia contra débito em dobro.
create table debits (
    id           bigserial   primary key,
    transfer_id  bigint      not null,
    from_key     varchar(40) not null references accounts (pix_key),
    amount_cents bigint      not null,
    created_at   timestamptz not null default now()
);

-- Estorno é uma linha nova, não um DELETE do débito (slide 22).
create table refunds (
    id           bigserial   primary key,
    debit_id     bigint      not null references debits (id),
    transfer_id  bigint      not null,
    amount_cents bigint      not null,
    created_at   timestamptz not null default now()
);
