select e.account_id
from ods.events e
window w as (partition by e.account_id order by e.event_time)
qualify row_number() over w = 1;
