-- Prazo do passo atual da saga: o SagaTimeoutScanner procura as vencidas.
alter table sagas add column deadline_at timestamptz;
create index sagas_deadline_idx on sagas (deadline_at) where deadline_at is not null;

-- Falha pedida em X-Simulate; acompanha todo comando desta transferência.
alter table transfers add column simulate varchar(32);

-- Fatos não se apagam (slide 23): cada mudança de estado vira uma linha nova.
-- Nunca UPDATE, nunca DELETE. Responde "por que a 1042 foi estornada?".
create table saga_transitions (
    id          bigserial   primary key,
    transfer_id bigint      not null references transfers (id),
    from_status varchar(32),
    to_status   varchar(32) not null,
    reason      text        not null,
    event_id    uuid,                 -- mensagem que causou a transição
    cid         text        not null, -- correlation id
    app_version text        not null, -- git sha do serviço que decidiu
    at          timestamptz not null default now()
);
create index saga_transitions_transfer_idx on saga_transitions (transfer_id);

-- Em produção, a aplicação conecta com um usuário sem esses privilégios (slide 23):
revoke update, delete, truncate on saga_transitions from current_user;

-- Aqui o usuário do docker compose é superusuário, e superusuário ignora o revoke.
-- O trigger garante a regra para qualquer um.
create function saga_transitions_append_only() returns trigger
language plpgsql as $$
begin
    raise exception 'saga_transitions é só de inserção: % não é permitido', tg_op;
end
$$;

create trigger saga_transitions_no_update_delete
    before update or delete on saga_transitions
    for each row execute function saga_transitions_append_only();

create trigger saga_transitions_no_truncate
    before truncate on saga_transitions
    for each statement execute function saga_transitions_append_only();
