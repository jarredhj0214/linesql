BEGIN;
SET CONSTRAINTS accounting.invoice_order_fk, accounting.invoice_customer_fk DEFERRED;
INSERT INTO accounting.invoice_audit (id)
SELECT id FROM staging.invoice_events WHERE accepted;
SET CONSTRAINTS ALL IMMEDIATE;
PREPARE TRANSACTION 'invoice-batch;2026-11-17';
COMMIT PREPARED 'invoice-batch;2026-11-17';
