with src as (
  select user_id, name, event_time
  from ods.user_delta
  where dt = '2026-08-24'
)
merge into ads.user_dim t
using src s
on t.user_id = s.user_id
when matched then update set name = s.name, updated_at = s.event_time
when not matched then insert (user_id, name, updated_at) values (s.user_id, s.name, s.event_time);
