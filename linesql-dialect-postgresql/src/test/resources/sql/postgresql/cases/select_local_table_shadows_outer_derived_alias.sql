WITH starter AS (SELECT id FROM live.accounts)
SELECT e.id, (
    SELECT MAX(e.id) FROM archive.events e WHERE e.id > 0
) AS flag
FROM starter e;
