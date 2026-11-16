CREATE TABLE mart.user_events (user_id BIGINT, event_date DATE, event_type STRING)
WRITE LOCALLY ORDERED BY ZORDER(user_id, event_date)
STORED BY ICEBERG STORED AS ORC;
