WITH accounts AS (SELECT a.account_id, a.region FROM crm.accounts a),
     invoices AS (SELECT i.customer_id, i.amount FROM billing.invoices i)
SELECT * FROM accounts a JOIN invoices i ON a.account_id = i.customer_id
ORDER BY 4;
