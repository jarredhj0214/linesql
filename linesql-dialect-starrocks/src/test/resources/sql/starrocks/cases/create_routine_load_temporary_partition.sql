create routine load mart.load_user_events_tp on ods.user_events
columns terminated by ",",
columns(event_id, user_id, event_time, dt = date_format(event_time, '%Y-%m-%d'))
where event_time >= '2026-08-24 00:00:00'
temporary partition(tp20260824)
properties (
  "format" = "csv",
  "partial_update" = "false"
)
from kafka (
  "kafka_broker_list" = "broker-1:9092",
  "kafka_topic" = "user_events"
);
