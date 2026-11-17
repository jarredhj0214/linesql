WITH q AS (SELECT e.source_key, e.group_id FROM ods.events e)
SELECT MIN(q.source_key) AS group_id
FROM q
GROUP BY group_id
HAVING group_id > 0
ORDER BY group_id;
