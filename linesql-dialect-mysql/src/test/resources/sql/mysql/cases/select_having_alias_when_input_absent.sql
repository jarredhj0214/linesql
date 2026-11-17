WITH q AS (SELECT e.source_key, e.group_id FROM ods.events e)
SELECT MIN(q.source_key) AS max_key
FROM q
GROUP BY group_id
HAVING max_key > 0
ORDER BY max_key;
