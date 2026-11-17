WITH q AS (SELECT e.* FROM live.events e)
TABLE q UNION ALL SELECT a.id, a.event_time FROM archive.events a;
