select dt, platform, count(distinct user_id) as uv
from ods.user_events
group by cube(dt, platform);
