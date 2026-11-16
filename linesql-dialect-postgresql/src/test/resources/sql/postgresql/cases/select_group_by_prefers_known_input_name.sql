WITH known AS (SELECT a.region, a.channel FROM ods.events a)
SELECT lower(k.channel) AS region, count(*) AS total FROM known k
GROUP BY region, lower(k.channel) ORDER BY region;
