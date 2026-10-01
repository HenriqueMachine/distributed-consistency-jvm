-- Cada mensagem morta, com tudo o que é preciso para investigar e resgatar (slide 37).
create table dead_letters (
    id               bigserial    primary key,
    dlt_topic        varchar(128) not null,
    dlt_partition    int          not null,
    dlt_offset       bigint       not null,
    original_topic   varchar(128) not null,
    message_key      varchar(64)  not null,
    message_id       uuid,
    message_type     varchar(64),
    payload          text         not null,
    error            text         not null,
    transfer_id      bigint,
    status           varchar(16)  not null,
    received_at      timestamptz  not null default now(),
    republished_by   varchar(80),
    republish_reason text,
    republished_at   timestamptz,
    unique (dlt_topic, dlt_partition, dlt_offset)
);

create index dead_letters_transfer_idx on dead_letters (transfer_id);
