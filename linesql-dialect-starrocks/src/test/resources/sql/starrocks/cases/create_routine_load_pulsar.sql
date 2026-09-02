create routine load mart.load_user_events_pulsar on ods.user_events
columns terminated by ",",
rows terminated by "\n",
columns(event_id, user_id, event_time, dt = str_to_date(event_time, '%Y-%m-%d'))
where event_time > '2026-08-24 00:00:00'
properties (
  "format" = "csv",
  "desired_concurrent_number" = "3"
)
from pulsar (
  "pulsar_service_url" = "pulsar://broker-1:6650",
  "pulsar_topic" = "persistent://public/default/user_events",
  "pulsar_subscription" = "sr_user_events"
);
