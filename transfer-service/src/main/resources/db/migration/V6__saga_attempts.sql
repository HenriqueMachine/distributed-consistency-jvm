-- Quantas vezes o comando do passo atual já foi enviado (reenvio após timeout, passo 5).
alter table sagas add column attempts int not null default 0;
