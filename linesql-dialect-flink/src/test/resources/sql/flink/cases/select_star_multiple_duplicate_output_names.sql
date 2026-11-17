WITH accounts AS (SELECT a.account_id AS id, a.region FROM crm.accounts a),
     invoices AS (SELECT i.customer_id AS id, i.amount FROM billing.invoices i)
SELECT * FROM accounts a JOIN invoices i ON a.id = i.id
ORDER BY 3;
