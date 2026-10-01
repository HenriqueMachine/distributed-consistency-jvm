-- Correlation id em árvore (slide 41): vai no header x-cid de cada mensagem publicada.
alter table outbox add column cid varchar(160);

-- O cid da mensagem morta: o resgate republica com um filho dele (….RPB-xx).
alter table dead_letters add column cid varchar(160);
