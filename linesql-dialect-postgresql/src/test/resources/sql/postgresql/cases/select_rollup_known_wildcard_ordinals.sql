WITH projected AS (SELECT e.channel, e.region FROM source.events e)
SELECT p.*, count(*) AS event_count
FROM projected p
GROUP BY ROLLUP (1, 2);
