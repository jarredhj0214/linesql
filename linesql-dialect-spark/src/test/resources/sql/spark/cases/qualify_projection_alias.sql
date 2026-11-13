select e.account_id, row_number() over w as rn
from ods.events e
window w as (partition by e.account_id order by e.event_time)
qualify rn = 1;
