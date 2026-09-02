create routine load mart.load_user_events on user_events
columns(event_id, user_id)
where event_id is not null
from kafka (
  "kafka_broker_list" = "broker-1:9092",
  "kafka_topic" = "user_events"
)
