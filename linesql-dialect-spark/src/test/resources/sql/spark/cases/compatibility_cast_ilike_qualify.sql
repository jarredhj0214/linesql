select id::varchar as id_text,
       row_number() over(partition by event_type order by event_time) as rn
from ods.user_events
where event_name ilike '%login%'
qualify row_number() over(partition by event_type order by event_time) = 1
