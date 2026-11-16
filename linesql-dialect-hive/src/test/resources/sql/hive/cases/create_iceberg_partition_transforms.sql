CREATE EXTERNAL TABLE mart.events (
    event_time TIMESTAMP,
    user_id BIGINT,
    region STRING
)
PARTITIONED BY SPEC (DAY(event_time), BUCKET(16, user_id), region)
STORED BY ICEBERG;
