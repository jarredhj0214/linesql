SELECT q.gross
FROM sales.orders o, LATERAL (SELECT amount + tax AS gross) q;
