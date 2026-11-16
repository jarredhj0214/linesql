SELECT y.gross_total
FROM sales.orders o,
LATERAL (SELECT o.amount + o.tax AS gross) x,
LATERAL (SELECT x.gross + o.shipping AS gross_total) y;
