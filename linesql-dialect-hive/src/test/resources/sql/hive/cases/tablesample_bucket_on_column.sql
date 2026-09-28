select e.id, e.event_time
from ods.events tablesample(bucket 3 out of 32 on user_id) e
