-- Uma conta por chave Pix. Os participantes da apresentação entram por POST /participants.
create table accounts (
    pix_key       varchar(40) primary key,
    name          varchar(120) not null,
    balance_cents bigint       not null check (balance_cents >= 0),
    created_at    timestamptz  not null default now()
);

-- Bia e Henrique: os personagens dos slides.
insert into accounts (pix_key, name, balance_cents) values
    ('bia',   'Bia',   100000),
    ('henrique', 'Henrique',  50000);
