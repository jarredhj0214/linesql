select dt, platform, count(*) as pv
from ods.user_events
group by rollup(dt, platform);
