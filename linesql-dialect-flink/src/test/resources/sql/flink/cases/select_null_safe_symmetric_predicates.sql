SELECT o.id
FROM orders AS o
JOIN customers AS c ON o.customer_id IS NOT DISTINCT FROM c.id
WHERE o.amount BETWEEN SYMMETRIC c.lower_amount AND c.upper_amount
  AND o.code LIKE c.code_pattern ESCAPE '!';
