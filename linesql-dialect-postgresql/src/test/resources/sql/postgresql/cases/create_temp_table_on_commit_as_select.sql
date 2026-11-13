CREATE TEMP TABLE tmp_customer_totals (customer, total)
USING heap WITH (fillfactor = 80)
ON COMMIT DROP
AS SELECT o.customer_id, SUM(o.amount) AS amount
FROM sales.orders AS o
GROUP BY o.customer_id;
