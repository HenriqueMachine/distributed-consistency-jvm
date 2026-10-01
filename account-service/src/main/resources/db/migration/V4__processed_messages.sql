-- Idempotência, camada 1 (slide 30): um registro por messageId (eventId) processado,
-- gravado na mesma transação do efeito. Conflito na PK = duplicata.
create table processed_messages (
    message_id   uuid        primary key,
    message_type varchar(64) not null,
    processed_at timestamptz not null default now()
);
