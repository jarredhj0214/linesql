select dt, platform, count(*) as pv
from ods.user_events
group by dt, platform with rollup;
