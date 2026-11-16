SELECT o.id, q.gross
FROM sales.orders o
CROSS JOIN LATERAL (SELECT o.amount + o.tax AS gross) q;
