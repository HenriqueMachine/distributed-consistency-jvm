-- Caixa de saída (slide 25): a mensagem é gravada junto com o dado, no mesmo commit.
-- O OutboxRelay publica as linhas com published_at nulo e depois as marca.
create table outbox (
    id           uuid         primary key,  -- é o messageId (eventId) que vai no header Kafka
    topic        varchar(128) not null,
    message_key  varchar(64)  not null,     -- transferId
    message_type varchar(64)  not null,
    payload      text         not null,
    simulate     varchar(32),
    created_at   timestamptz  not null default now(),
    published_at timestamptz
);

create index outbox_pending_idx on outbox (created_at) where published_at is null;
