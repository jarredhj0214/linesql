select account_id,
       last_value(status) respect nulls over (partition by account_id order by event_time) as last_status
from ods.account_events;
