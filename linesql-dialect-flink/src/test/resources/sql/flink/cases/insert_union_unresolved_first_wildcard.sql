INSERT INTO mart.combined (event_id, occurred_at)
SELECT * FROM ods.live UNION ALL SELECT id, event_time FROM ods.history;
