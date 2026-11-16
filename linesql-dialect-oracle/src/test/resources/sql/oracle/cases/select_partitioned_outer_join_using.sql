SELECT month_id, s.units
FROM facts.sales s PARTITION BY (s.store_id, s.product_id)
RIGHT JOIN dims.calendar c USING (month_id)
WHERE month_id > 0;
