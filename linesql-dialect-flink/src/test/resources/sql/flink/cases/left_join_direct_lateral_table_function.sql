SELECT o.id, f.part
FROM orders AS o
LEFT JOIN LATERAL split_code(o.code) AS f(part) ON TRUE;
