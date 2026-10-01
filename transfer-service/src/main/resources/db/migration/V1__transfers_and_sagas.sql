-- Transferências começam em 1042: a primeira de um ambiente novo é a da história.
create sequence transfer_id_seq start with 1042;

create table transfers (
    id           bigint primary key default nextval('transfer_id_seq'),
    from_key     varchar(64) not null,
    to_key       varchar(64) not null,
    amount_cents bigint      not null check (amount_cents > 0),
    created_at   timestamptz not null default now()
);

create table sagas (
    transfer_id bigint primary key references transfers (id),
    state       varchar(32) not null,
    updated_at  timestamptz not null default now()
);
