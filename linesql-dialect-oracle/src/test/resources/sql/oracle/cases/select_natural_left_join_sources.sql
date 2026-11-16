SELECT c.customer_name, o.amount
FROM ods.customers c NATURAL LEFT OUTER JOIN ods.orders o;
