SELECT id,
       ARRAY(SELECT p.amount FROM sales.payments AS p WHERE p.account_id = a.id) AS amounts
FROM mart.accounts AS a;
