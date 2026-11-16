WITH known AS (SELECT a.region, a.channel FROM ods.events a)
SELECT k.channel AS region FROM known k ORDER BY lower(region), (1);
