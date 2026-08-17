select dt,
       platform,
       lpad(bin(GROUPING__ID ^ 3), 2, 0) as grouping_id,
       count(distinct user_id) as uv
from ods.user_events
where dt = '#day#'
  and platform in ('android', 'ios')
group by dt, platform grouping sets ((dt, platform), (dt))
