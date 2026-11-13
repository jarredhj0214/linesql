SELECT o.id, u.item, u.ord
FROM orders AS o
CROSS JOIN UNNEST(o.items) WITH ORDINALITY AS u(item, ord)
WHERE u.item IS NOT NULL;
