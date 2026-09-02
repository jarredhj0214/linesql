SELECT user_id, event_type
FROM dwd.user_events TABLESAMPLE (100 ROWS)
