(SELECT e.event_id, e.observed_at FROM ods.events e
 UNION ALL SELECT h.event_id, h.observed_at FROM history.events h)
ORDER BY 2;
