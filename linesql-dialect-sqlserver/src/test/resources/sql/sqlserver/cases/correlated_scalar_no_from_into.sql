SELECT o.id, (SELECT o.adjustment + o.tax) AS total
INTO sales.order_totals
FROM sales.orders o;
