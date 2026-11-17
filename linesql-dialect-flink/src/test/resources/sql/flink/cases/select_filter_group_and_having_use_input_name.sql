WITH c AS (SELECT e.region, e.channel FROM ods.events e)
SELECT lower(c.channel) AS region, count(*) AS n FROM c
WHERE region <> 'west' GROUP BY region, lower(c.channel)
HAVING count(DISTINCT region) > 0;
