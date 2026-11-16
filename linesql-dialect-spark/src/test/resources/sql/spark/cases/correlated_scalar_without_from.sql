SELECT o.id,
       (SELECT o.adjustment + o.tax) AS total
FROM sales.orders o;
