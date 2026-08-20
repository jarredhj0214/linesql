select
  vehicle_category_code,
  level_1_channel_code
from (
  select vehicle_category_code, appoint_code
  from dwd.test_drive
) t_test
left join (
  select *
  from dwd.appoint_relation
) t_appoint
  on t_test.appoint_code = t_appoint.appoint_code
left join (
  select channel_code, first_channel_tag_code as level_1_channel_code
  from dim.channel
) t2
  on t_appoint.channel_code = t2.channel_code
