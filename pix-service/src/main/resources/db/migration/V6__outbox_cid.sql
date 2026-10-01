-- Correlation id em árvore (slide 41): vai no header x-cid de cada mensagem publicada.
alter table outbox add column cid varchar(160);
