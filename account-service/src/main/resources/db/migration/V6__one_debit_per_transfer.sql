-- Idempotência, camada 2 (slide 30): "já existe débito desta transferência?".
-- Um reenvio com eventId novo passa pela camada 1, mas para aqui.
alter table debits add constraint uq_debit_transfer unique (transfer_id);

-- Um estorno por débito: RefundDebit também pode chegar duas vezes (slide 22).
alter table refunds add constraint uq_refund_debit unique (debit_id);
