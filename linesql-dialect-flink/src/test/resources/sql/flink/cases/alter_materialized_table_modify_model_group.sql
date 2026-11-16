ALTER MATERIALIZED TABLE mart.signals MODIFY (
    event_time AS TO_TIMESTAMP(raw_time) AFTER category,
    WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND
);
