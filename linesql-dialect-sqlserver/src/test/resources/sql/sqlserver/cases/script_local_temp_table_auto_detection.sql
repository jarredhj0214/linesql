CREATE TABLE #active_events (event_id int, amount decimal(10,2));
INSERT INTO #active_events (event_id, amount)
SELECT id, total FROM dbo.events WHERE active = 1;
SELECT event_id, amount FROM #active_events WHERE amount > 0 ORDER BY amount;
DROP TABLE IF EXISTS #active_events;
