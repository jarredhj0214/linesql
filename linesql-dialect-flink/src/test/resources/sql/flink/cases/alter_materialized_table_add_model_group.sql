ALTER MATERIALIZED TABLE mart.signals ADD (
    category STRING METADATA VIRTUAL FIRST,
    event_time AS TO_TIMESTAMP(raw_time) AFTER category,
    PRIMARY KEY (id) NOT ENFORCED,
    WATERMARK FOR event_time AS event_time - INTERVAL '2' SECOND
);
