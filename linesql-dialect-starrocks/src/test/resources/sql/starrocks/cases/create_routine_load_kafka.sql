create routine load mart.load_user_events on ods.user_events
columns terminated by ",",
columns(event_id, user_id, event_time)
where event_id is not null
properties ("desired_concurrent_number" = "3")
from kafka (
  "kafka_broker_list" = "broker-1:9092",
  "kafka_topic" = "user_events"
);
