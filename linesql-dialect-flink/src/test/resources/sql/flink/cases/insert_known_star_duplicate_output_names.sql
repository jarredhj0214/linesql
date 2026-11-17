INSERT INTO audit.summary(account_id, zone, invoice_customer_id, invoice_amount, source_key)
WITH accounts AS (SELECT a.account_id AS id, a.region FROM crm.accounts a),
     invoices AS (SELECT i.customer_id AS id, i.amount FROM billing.invoices i)
SELECT *, a.id AS source_key
FROM accounts a JOIN invoices i ON a.id = i.id;
