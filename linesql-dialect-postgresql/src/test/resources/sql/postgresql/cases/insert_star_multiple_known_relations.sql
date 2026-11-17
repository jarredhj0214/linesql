WITH accounts AS (SELECT a.account_id, a.region FROM crm.accounts a),
     invoices AS (SELECT i.customer_id, i.amount FROM billing.invoices i)
INSERT INTO audit.summary(customer, zone, invoice_customer_id, invoice_amount)
SELECT * FROM accounts, invoices;
