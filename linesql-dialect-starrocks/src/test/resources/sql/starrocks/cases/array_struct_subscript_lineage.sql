select
  events[1].event_id as first_event_id,
  events[1].payload['sku'] as first_event_sku
from dwd.user_events;
