select account_id,
       first_value(amount) ignore nulls over (partition by account_id order by event_time) as first_amount
from ods.account_events;
