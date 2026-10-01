-- Idempotência, camada 2: um Pix por transferência. O SPI faz o mesmo com a chave de
-- idempotência e devolve o mesmo endToEndId (slide 28).
alter table pix_transfers add constraint uq_pix_transfer unique (transfer_id);
