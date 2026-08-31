alter routine load for mart.load_user_events
columns terminated by ",",
rows terminated by "\n",
columns(event_id, user_id, event_time),
temporary partition (p202608),
where event_id is not null,
properties (
  "desired_concurrent_number" = "4"
),
from kafka (
  "kafka_partitions" = "0,1",
  "kafka_offsets" = "OFFSET_BEGINNING,OFFSET_END"
);
