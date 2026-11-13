SELECT o.id
FROM sales.orders PARTITION FOR (DATE '2026-11-01') o
WHERE o.status = 'PAID';
