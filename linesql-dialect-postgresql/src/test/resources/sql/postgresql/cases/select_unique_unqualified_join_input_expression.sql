WITH src AS (SELECT o.region FROM ods.orders o),
caps AS (SELECT l.cap FROM ods.limits l)
SELECT lower(region) AS normalized, l.cap
FROM src s CROSS JOIN caps l
ORDER BY lower(region);
