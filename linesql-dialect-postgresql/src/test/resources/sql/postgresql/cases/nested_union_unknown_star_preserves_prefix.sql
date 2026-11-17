(SELECT a.id, a.*, a.note FROM live.events a
 UNION ALL SELECT b.id, b.name, b.event_date, b.note FROM history.events b)
UNION ALL SELECT c.id, c.name, c.event_date, c.note FROM latest.events c
ORDER BY 1, 2;
