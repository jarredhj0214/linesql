WITH q AS (SELECT e.* FROM live.events e WHERE EXISTS
    (SELECT 1 FROM access.allowed a WHERE a.event_id = e.id)),
forwarded AS (SELECT * FROM q)
SELECT forwarded.* FROM forwarded
UNION ALL SELECT a.id, a.event_time FROM archive.events a;
