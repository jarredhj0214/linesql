WITH q AS (SELECT id FROM events)
SELECT q.id, (SELECT s.amount FROM snapshots s WHERE s.id = q.id) AS amount
FROM q;
