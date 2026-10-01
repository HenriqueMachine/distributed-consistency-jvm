-- Uma linha da outbox só é publicada a partir de available_at. Normalmente é o próprio
-- instante da gravação; a simulação DEBIT_SLOW usa isso para atrasar a resposta.
alter table outbox add column available_at timestamptz not null default now();

drop index outbox_pending_idx;
create index outbox_pending_idx on outbox (available_at) where published_at is null;
