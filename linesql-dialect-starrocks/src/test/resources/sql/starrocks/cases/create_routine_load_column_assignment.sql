create routine load mart.load_user_events_json on ods.user_events
columns(event_id, raw_ts, event_time = str_to_date(raw_ts, '%Y-%m-%d %H:%i:%s'))
where event_id is not null
properties (
  "format" = "json",
  "jsonpaths" = "[\"$.event_id\", \"$.raw_ts\"]",
  "strip_outer_array" = "true"
)
from kafka (
  "kafka_broker_list" = "broker-1:9092",
  "kafka_topic" = "user_events_json"
)
