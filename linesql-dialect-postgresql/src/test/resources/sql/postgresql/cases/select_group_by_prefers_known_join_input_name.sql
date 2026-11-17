WITH src AS (SELECT o.region, o.channel FROM ods.orders o),
caps AS (SELECT l.cap FROM ods.limits l)
SELECT lower(s.channel) AS region, max(l.cap) AS cap
FROM src s CROSS JOIN caps l
GROUP BY region, lower(s.channel)
ORDER BY region;
