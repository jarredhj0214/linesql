SELECT v.price, v.column2 AS charge
FROM sales.orders AS o
CROSS JOIN LATERAL (VALUES (o.unit_price, o.tax), (o.discount, o.shipping)) AS v(price)
WHERE v.column2 > 0;
